package com.finance.backend.service;

import com.finance.backend.model.MarketType;
import com.finance.backend.model.Portfolio;
import com.finance.backend.model.PortfolioAssetDailySnapshot;
import com.finance.backend.model.PortfolioDailySnapshot;
import com.finance.backend.model.PortfolioPosition;
import com.finance.backend.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.backend.repository.PortfolioDailySnapshotRepository;
import com.finance.backend.repository.PortfolioPositionRepository;
import com.finance.backend.repository.PortfolioRepository;
import com.finance.backend.service.AssetPricingPort.AssetKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioBackfillService {

    private static final int PRICE_LOOKBACK_DAYS = 7;
    private static final LocalTime SNAPSHOT_TIME = LocalTime.of(23, 0);

    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final PortfolioDailySnapshotRepository dailySnapshotRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final HistoricalPricingPort historicalPricingPort;
    private final AssetPricingPort assetPricingPort;
    private final SnapshotCalculationService calculator;
    private final PortfolioBackfillTracker tracker;

    public record LotChangedEvent(Long portfolioId, LocalDate fromDate) {
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLotChanged(LotChangedEvent event) {
        Long portfolioId = event.portfolioId();
        tracker.start(portfolioId);
        try {
            wipeSnapshotsFrom(portfolioId, event.fromDate());
            backfillSinceDate(portfolioId, event.fromDate());
            snapshotToday(portfolioId);
        } catch (Exception e) {
            log.warn("Recompute failed for portfolio {} from {}: {}",
                    portfolioId, event.fromDate(), e.getMessage(), e);
        } finally {
            tracker.finish(portfolioId);
        }
    }

    private void wipeSnapshotsFrom(Long portfolioId, LocalDate from) {
        if (from == null) return;
        dailySnapshotRepository.deleteByPortfolioIdAndSnapshotDateGreaterThanEqual(portfolioId, from);
        assetSnapshotRepository.deleteByPortfolioIdAndSnapshotDateGreaterThanEqual(portfolioId, from);
    }

    public void snapshotToday(Long portfolioId) {
        LocalDate today = LocalDate.now();
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return;
        boolean dailyExists = dailySnapshotRepository.existsByPortfolioIdAndSnapshotDate(portfolioId, today);
        boolean assetExists = assetSnapshotRepository.existsByPortfolioIdAndSnapshotDate(portfolioId, today);
        if (dailyExists && assetExists) return;

        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        List<PortfolioPosition> active = activePositionsOn(positions, today);
        if (active.isEmpty()) return;

        List<AssetKey> keys = active.stream().map(PortfolioBackfillService::toKey).distinct().toList();
        Map<AssetKey, BigDecimal> dayPrices = assetPricingPort.getPricesTry(keys);
        LocalDateTime ts = LocalDateTime.now();

        if (!assetExists) writeAssetSnapshots(portfolioId, active, ts, dayPrices);
        if (!dailyExists) writeAggregateSnapshot(portfolio, ts, active, dayPrices);
    }

    public void backfillSinceDate(Long portfolioId, LocalDate from) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return;

        LocalDate end = LocalDate.now().minusDays(1);
        if (from == null || from.isAfter(end)) return;

        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        if (positions.isEmpty()) return;

        Map<AssetKey, Map<LocalDate, BigDecimal>> seriesByKey = loadHistoricalSeries(positions, from, end);

        for (LocalDate day = from; !day.isAfter(end); day = day.plusDays(1)) {
            backfillDay(portfolio, day, positions, seriesByKey);
        }
    }

    private Map<AssetKey, Map<LocalDate, BigDecimal>> loadHistoricalSeries(
            List<PortfolioPosition> positions, LocalDate from, LocalDate to) {
        Map<AssetKey, Map<LocalDate, BigDecimal>> result = new HashMap<>();
        for (PortfolioPosition pos : positions) {
            AssetKey key = toKey(pos);
            if (result.containsKey(key)) continue;
            MarketType type = pos.getAssetType().marketType();
            result.put(key, historicalPricingPort.getPriceSeries(type, pos.getAssetCode(), from, to));
        }
        return result;
    }

    private void backfillDay(Portfolio portfolio, LocalDate day,
                             List<PortfolioPosition> allPositions,
                             Map<AssetKey, Map<LocalDate, BigDecimal>> seriesByKey) {
        Long portfolioId = portfolio.getId();
        boolean dailyExists = dailySnapshotRepository.existsByPortfolioIdAndSnapshotDate(portfolioId, day);
        boolean assetExists = assetSnapshotRepository.existsByPortfolioIdAndSnapshotDate(portfolioId, day);
        if (dailyExists && assetExists) return;

        List<PortfolioPosition> active = activePositionsOn(allPositions, day);
        if (active.isEmpty()) return;

        Map<AssetKey, BigDecimal> dayPrices = pricesForDay(active, day, seriesByKey);
        LocalDateTime ts = day.atTime(SNAPSHOT_TIME);

        if (!assetExists) writeAssetSnapshots(portfolioId, active, ts, dayPrices);
        if (!dailyExists) writeAggregateSnapshot(portfolio, ts, active, dayPrices);
    }

    private void writeAssetSnapshots(Long portfolioId, List<PortfolioPosition> active,
                                       LocalDateTime ts, Map<AssetKey, BigDecimal> dayPrices) {
        Map<AssetKey, List<PortfolioPosition>> byAsset = groupByAsset(active);
        for (Map.Entry<AssetKey, List<PortfolioPosition>> entry : byAsset.entrySet()) {
            BigDecimal price = dayPrices.get(entry.getKey());
            if (price == null) continue;
            PortfolioPosition first = entry.getValue().get(0);
            BigDecimal totalQty = sumField(entry.getValue(), PortfolioPosition::getQuantity);
            BigDecimal totalCost = sumField(entry.getValue(), PortfolioPosition::entryValue);
            PortfolioAssetDailySnapshot snap = calculator.buildAggregatedAssetSnapshot(
                    portfolioId, first.getAssetType(), first.getAssetCode(),
                    ts, totalQty, totalCost, price);
            assetSnapshotRepository.save(snap);
        }
    }

    private static BigDecimal sumField(List<PortfolioPosition> lots,
                                        java.util.function.Function<PortfolioPosition, BigDecimal> extractor) {
        BigDecimal total = BigDecimal.ZERO;
        for (PortfolioPosition lot : lots) {
            total = total.add(extractor.apply(lot));
        }
        return total;
    }

    private void writeAggregateSnapshot(Portfolio portfolio, LocalDateTime ts,
                                          List<PortfolioPosition> active,
                                          Map<AssetKey, BigDecimal> dayPrices) {
        PortfolioDailySnapshot snap = calculator.buildAggregateSnapshotAt(portfolio, ts, active, dayPrices);
        dailySnapshotRepository.save(snap);
    }

    private static Map<AssetKey, List<PortfolioPosition>> groupByAsset(List<PortfolioPosition> positions) {
        Map<AssetKey, List<PortfolioPosition>> grouped = new LinkedHashMap<>();
        for (PortfolioPosition pos : positions) {
            grouped.computeIfAbsent(toKey(pos), k -> new ArrayList<>()).add(pos);
        }
        return grouped;
    }

    private static List<PortfolioPosition> activePositionsOn(List<PortfolioPosition> all, LocalDate day) {
        return all.stream()
                .filter(p -> p.getEntryDate() != null && !p.getEntryDate().toLocalDate().isAfter(day))
                .toList();
    }

    private static Map<AssetKey, BigDecimal> pricesForDay(List<PortfolioPosition> positions, LocalDate day,
                                                            Map<AssetKey, Map<LocalDate, BigDecimal>> seriesByKey) {
        Map<AssetKey, BigDecimal> result = new HashMap<>();
        for (PortfolioPosition pos : positions) {
            AssetKey key = toKey(pos);
            if (result.containsKey(key)) continue;
            BigDecimal price = nearestPriceOnOrBefore(seriesByKey.get(key), day);
            if (price != null) result.put(key, price);
        }
        return result;
    }

    private static BigDecimal nearestPriceOnOrBefore(Map<LocalDate, BigDecimal> series, LocalDate day) {
        if (series == null || series.isEmpty()) return null;
        LocalDate cursor = day;
        for (int i = 0; i <= PRICE_LOOKBACK_DAYS; i += 1) {
            BigDecimal price = series.get(cursor);
            if (price != null) return price;
            cursor = cursor.minusDays(1);
        }
        return null;
    }

    private static AssetKey toKey(PortfolioPosition pos) {
        return new AssetKey(pos.getAssetType().marketType(), pos.getAssetCode());
    }
}
