package com.finance.portfolio.service;
import com.finance.market.core.service.HistoricalPricingPort;

import com.finance.portfolio.model.AssetType;

import com.finance.shared.service.AssetPricingPort;



import com.finance.common.model.MarketType;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.portfolio.repository.PortfolioRepository;
import com.finance.shared.service.AssetPricingPort.AssetKey;
import com.finance.portfolio.config.PortfolioProperties;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Log4j2
@Service
public class PortfolioBackfillService {

    private static final LocalTime SNAPSHOT_TIME = LocalTime.of(23, 0);

    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final PortfolioDailySnapshotRepository dailySnapshotRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final HistoricalPricingPort historicalPricingPort;
    private final AssetPricingPort assetPricingPort;
    private final SnapshotCalculationService calculator;
    private final PortfolioBackfillTracker tracker;
    private final TransactionTemplate transactionTemplate;
    private final Object[] portfolioLocks;
    private final int priceLookbackDays;
    private final int lockStripes;

    public PortfolioBackfillService(PortfolioRepository portfolioRepository,
                                     PortfolioPositionRepository positionRepository,
                                     PortfolioDailySnapshotRepository dailySnapshotRepository,
                                     PortfolioAssetDailySnapshotRepository assetSnapshotRepository,
                                     HistoricalPricingPort historicalPricingPort,
                                     AssetPricingPort assetPricingPort,
                                     SnapshotCalculationService calculator,
                                     PortfolioBackfillTracker tracker,
                                     PlatformTransactionManager transactionManager,
                                     PortfolioProperties portfolioProperties) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.dailySnapshotRepository = dailySnapshotRepository;
        this.assetSnapshotRepository = assetSnapshotRepository;
        this.historicalPricingPort = historicalPricingPort;
        this.assetPricingPort = assetPricingPort;
        this.calculator = calculator;
        this.tracker = tracker;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.priceLookbackDays = portfolioProperties.getBackfill().getPriceLookbackDays();
        this.lockStripes = portfolioProperties.getBackfill().getLockStripes();
        this.portfolioLocks = new Object[lockStripes];
        for (int i = 0; i < lockStripes; i++) portfolioLocks[i] = new Object();
    }

    private Object lockFor(Long portfolioId) {
        return portfolioLocks[Math.floorMod(portfolioId.hashCode(), lockStripes)];
    }

    public record LotChangedEvent(Long portfolioId, com.finance.portfolio.model.AssetType assetType,
                                  String assetCode, LocalDate fromDate, boolean visibleToUi) {
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLotChanged(LotChangedEvent event) {
        Long portfolioId = event.portfolioId();
        synchronized (lockFor(portfolioId)) {
            if (event.visibleToUi()) tracker.start(portfolioId, event.assetType(), event.assetCode());
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    wipeSnapshotsFrom(portfolioId, event.fromDate());
                    backfillSinceDate(portfolioId, event.fromDate());
                    snapshotToday(portfolioId);
                });
            } catch (Exception e) {
                log.warn("Recompute failed for portfolio {} from {}: {}",
                        portfolioId, event.fromDate(), e.getMessage(), e);
            } finally {
                if (event.visibleToUi()) tracker.finish(portfolioId, event.assetType(), event.assetCode());
            }
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

        List<AssetKey> keys = active.stream().map(PortfolioPosition::toAssetKey).distinct().toList();
        Map<AssetKey, BigDecimal> dayPrices = assetPricingPort.getPricesTry(keys);
        LocalDateTime ts = LocalDateTime.now();

        if (!assetExists) {
            Map<AssetKey, List<PortfolioPosition>> byAsset = groupByAsset(active);
            List<PortfolioAssetDailySnapshot> batch = new ArrayList<>();
            for (Map.Entry<AssetKey, List<PortfolioPosition>> entry : byAsset.entrySet()) {
                BigDecimal price = dayPrices.get(entry.getKey());
                if (price == null) continue;
                PortfolioPosition first = entry.getValue().get(0);
                BigDecimal totalQty = sumField(entry.getValue(), PortfolioPosition::getQuantity);
                BigDecimal totalCost = sumField(entry.getValue(), PortfolioPosition::entryValue);
                batch.add(calculator.buildAggregatedAssetSnapshot(
                        portfolioId, first.getAssetType(), first.getAssetCode(), first.getTrackedAsset(),
                        ts, totalQty, totalCost, price));
            }
            assetSnapshotRepository.saveAll(batch);
        }
        if (!dailyExists) {
            dailySnapshotRepository.saveAll(List.of(calculator.buildAggregateSnapshotAt(portfolio, ts, active, dayPrices)));
        }
    }

    public void backfillSinceDate(Long portfolioId, LocalDate from) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return;

        LocalDate end = LocalDate.now().minusDays(1);
        if (from == null || from.isAfter(end)) return;

        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        if (positions.isEmpty()) return;

        Set<LocalDate> dailyExisting = Set.copyOf(dailySnapshotRepository.findExistingDates(portfolioId, from, end));
        Set<LocalDate> assetExisting = Set.copyOf(assetSnapshotRepository.findExistingDates(portfolioId, from, end));
        Map<AssetKey, Map<LocalDate, BigDecimal>> seriesByKey = loadHistoricalSeries(positions, from, end);
        Map<AssetKey, PortfolioAssetDailySnapshot> priorAssetByKey = preloadPriorAssetSnapshots(portfolioId, positions, from);

        List<PortfolioAssetDailySnapshot> assetBatch = new ArrayList<>();
        List<PortfolioDailySnapshot> dailyBatch = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(end); day = day.plusDays(1)) {
            collectDay(portfolio, day, positions, seriesByKey, dailyExisting, assetExisting,
                    assetBatch, dailyBatch, priorAssetByKey);
        }

        if (!assetBatch.isEmpty()) assetSnapshotRepository.saveAll(assetBatch);
        if (!dailyBatch.isEmpty()) dailySnapshotRepository.saveAll(dailyBatch);
    }

    private Map<AssetKey, PortfolioAssetDailySnapshot> preloadPriorAssetSnapshots(
            Long portfolioId, List<PortfolioPosition> positions, LocalDate from) {
        LocalDateTime cutoff = from.atStartOfDay();
        Map<AssetKey, PortfolioAssetDailySnapshot> result = new HashMap<>();
        for (PortfolioPosition pos : positions) {
            AssetKey key = pos.toAssetKey();
            if (result.containsKey(key)) continue;
            if (pos.getTrackedAsset() == null) continue;
            assetSnapshotRepository
                    .findFirstByPortfolioIdAndTrackedAssetIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                            portfolioId, pos.getTrackedAsset().getId(), cutoff)
                    .ifPresent(p -> result.put(key, p));
        }
        return result;
    }

    private Map<AssetKey, Map<LocalDate, BigDecimal>> loadHistoricalSeries(
            List<PortfolioPosition> positions, LocalDate from, LocalDate to) {
        Map<AssetKey, Map<LocalDate, BigDecimal>> result = new HashMap<>();
        for (PortfolioPosition pos : positions) {
            AssetKey key = pos.toAssetKey();
            if (result.containsKey(key)) continue;
            MarketType type = pos.getAssetType().marketType();
            result.put(key, historicalPricingPort.getPriceSeries(type, pos.getAssetCode(), from, to));
        }
        return result;
    }

    private void collectDay(Portfolio portfolio, LocalDate day,
                            List<PortfolioPosition> allPositions,
                            Map<AssetKey, Map<LocalDate, BigDecimal>> seriesByKey,
                            Set<LocalDate> dailyExisting, Set<LocalDate> assetExisting,
                            List<PortfolioAssetDailySnapshot> assetBatch,
                            List<PortfolioDailySnapshot> dailyBatch,
                            Map<AssetKey, PortfolioAssetDailySnapshot> priorAssetByKey) {
        boolean dailyExists = dailyExisting.contains(day);
        boolean assetExists = assetExisting.contains(day);
        if (dailyExists && assetExists) return;

        List<PortfolioPosition> active = activePositionsOn(allPositions, day);
        if (active.isEmpty()) return;

        Map<AssetKey, BigDecimal> dayPrices = pricesForDay(active, day, seriesByKey);
        LocalDateTime ts = day.atTime(SNAPSHOT_TIME);
        Long portfolioId = portfolio.getId();

        Map<AssetKey, PortfolioAssetDailySnapshot> aggregatePriors = new HashMap<>(priorAssetByKey);
        if (!assetExists) {
            collectAssetSnapshots(portfolioId, active, ts, dayPrices, assetBatch, priorAssetByKey);
        }
        if (!dailyExists) {
            dailyBatch.add(calculator.buildAggregateSnapshotAtWithPriors(portfolio, ts, active, dayPrices, aggregatePriors));
        }
    }

    private void collectAssetSnapshots(Long portfolioId, List<PortfolioPosition> active,
                                        LocalDateTime ts, Map<AssetKey, BigDecimal> dayPrices,
                                        List<PortfolioAssetDailySnapshot> batch,
                                        Map<AssetKey, PortfolioAssetDailySnapshot> priorAssetByKey) {
        Map<AssetKey, List<PortfolioPosition>> byAsset = groupByAsset(active);
        for (Map.Entry<AssetKey, List<PortfolioPosition>> entry : byAsset.entrySet()) {
            BigDecimal price = dayPrices.get(entry.getKey());
            if (price == null) continue;
            PortfolioPosition first = entry.getValue().get(0);
            BigDecimal totalQty = sumField(entry.getValue(), PortfolioPosition::getQuantity);
            BigDecimal totalCost = sumField(entry.getValue(), PortfolioPosition::entryValue);
            PortfolioAssetDailySnapshot prior = priorAssetByKey.get(entry.getKey());
            PortfolioAssetDailySnapshot snapshot = calculator.buildAggregatedAssetSnapshotWithPrior(
                    portfolioId, first.getAssetType(), first.getAssetCode(), first.getTrackedAsset(),
                    ts, totalQty, totalCost, price, prior);
            batch.add(snapshot);
            priorAssetByKey.put(entry.getKey(), snapshot);
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

    private static Map<AssetKey, List<PortfolioPosition>> groupByAsset(List<PortfolioPosition> positions) {
        Map<AssetKey, List<PortfolioPosition>> grouped = new LinkedHashMap<>();
        for (PortfolioPosition pos : positions) {
            grouped.computeIfAbsent(pos.toAssetKey(), k -> new ArrayList<>()).add(pos);
        }
        return grouped;
    }

    private static List<PortfolioPosition> activePositionsOn(List<PortfolioPosition> all, LocalDate day) {
        return all.stream()
                .filter(p -> p.getEntryDate() != null && !p.getEntryDate().toLocalDate().isAfter(day))
                .toList();
    }

    private Map<AssetKey, BigDecimal> pricesForDay(List<PortfolioPosition> positions, LocalDate day,
                                                            Map<AssetKey, Map<LocalDate, BigDecimal>> seriesByKey) {
        Map<AssetKey, BigDecimal> result = new HashMap<>();
        for (PortfolioPosition pos : positions) {
            AssetKey key = pos.toAssetKey();
            if (result.containsKey(key)) continue;
            BigDecimal price = pos.getEntryDate() != null && pos.getEntryDate().toLocalDate().equals(day)
                    ? pos.getEntryPrice()
                    : nearestPriceOnOrBefore(seriesByKey.get(key), day);
            if (price != null) result.put(key, price);
        }
        return result;
    }

    private BigDecimal nearestPriceOnOrBefore(Map<LocalDate, BigDecimal> series, LocalDate day) {
        if (series == null || series.isEmpty()) return null;
        LocalDate cursor = day;
        for (int i = 0; i <= priceLookbackDays; i += 1) {
            BigDecimal price = series.get(cursor);
            if (price != null) return price;
            cursor = cursor.minusDays(1);
        }
        return null;
    }

}
