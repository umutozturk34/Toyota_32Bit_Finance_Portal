package com.finance.portfolio.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.shared.service.AssetPricingPort.AssetKey;
import org.springframework.stereotype.Component;

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
import java.util.function.Function;

/**
 * Per-day building blocks for {@link PortfolioBackfillService}: collects a day's per-asset and
 * aggregate snapshots into reusable batches, loads historical price series and prior snapshots, and
 * resolves a day's price (entry price on the entry day, else the nearest price on or before the day
 * within the configured lookback). Emits a pre-close + zero pair on a lot's exit day.
 */
@Component
public class BackfillBatchCollector {

    private static final LocalTime SNAPSHOT_TIME = LocalTime.MIDNIGHT;

    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final HistoricalPricingPort historicalPricingPort;
    private final SnapshotCalculationService calculator;
    private final int priceLookbackDays;

    public BackfillBatchCollector(PortfolioAssetDailySnapshotRepository assetSnapshotRepository,
                                   HistoricalPricingPort historicalPricingPort,
                                   SnapshotCalculationService calculator,
                                   PortfolioProperties portfolioProperties) {
        this.assetSnapshotRepository = assetSnapshotRepository;
        this.historicalPricingPort = historicalPricingPort;
        this.calculator = calculator;
        this.priceLookbackDays = portfolioProperties.getBackfill().getPriceLookbackDays();
    }

    /** Appends a single day's missing per-asset and/or aggregate rows to the batches, skipping work already persisted. */
    public void collectDay(Portfolio portfolio, LocalDate day,
                           List<PortfolioPosition> allPositions,
                           List<DerivativePosition> allDerivatives,
                           Map<AssetKey, Map<LocalDate, BigDecimal>> seriesByKey,
                           Set<LocalDate> dailyExisting, Set<LocalDate> assetExisting,
                           List<PortfolioAssetDailySnapshot> assetBatch,
                           List<PortfolioDailySnapshot> dailyBatch,
                           Map<AssetKey, PortfolioAssetDailySnapshot> priorAssetByKey) {
        boolean dailyExists = dailyExisting.contains(day);
        boolean assetExists = assetExisting.contains(day);
        if (dailyExists && assetExists) return;

        List<PortfolioPosition> openedByDay = allPositions.stream()
                .filter(p -> p.getEntryDate() != null && !p.getEntryDate().toLocalDate().isAfter(day))
                .toList();
        List<DerivativePosition> derivativesOpenedByDay = allDerivatives.stream()
                .filter(d -> d.getEntryDate() != null && !d.getEntryDate().isAfter(day))
                .toList();
        if (openedByDay.isEmpty() && derivativesOpenedByDay.isEmpty()) return;

        List<PortfolioPosition> active = activePositionsOn(allPositions, day);
        Map<AssetKey, BigDecimal> dayPrices = pricesForDay(active, day, seriesByKey);
        LocalDateTime ts = day.atTime(SNAPSHOT_TIME);
        Long portfolioId = portfolio.getId();

        List<PortfolioAssetDailySnapshot> dayRows = new ArrayList<>();
        if (!assetExists) {
            collectAssetSnapshots(portfolioId, active, ts, dayPrices, assetBatch, priorAssetByKey, dayRows);
        } else if (!dailyExists) {
            dayRows.addAll(assetSnapshotRepository.findByPortfolioIdAndSnapshotDate(portfolioId, day));
        }
        if (!dailyExists) {
            dailyBatch.add(calculator.buildAggregateSnapshotAtFromRows(portfolio, ts, openedByDay, derivativesOpenedByDay, dayPrices, dayRows));
        }
    }

    public void collectAssetSnapshots(Long portfolioId, List<PortfolioPosition> active,
                                       LocalDateTime ts, Map<AssetKey, BigDecimal> dayPrices,
                                       List<PortfolioAssetDailySnapshot> batch,
                                       Map<AssetKey, PortfolioAssetDailySnapshot> priorAssetByKey,
                                       List<PortfolioAssetDailySnapshot> dayRows) {
        Map<AssetKey, List<PortfolioPosition>> byAsset = groupByAsset(active);
        for (Map.Entry<AssetKey, List<PortfolioPosition>> entry : byAsset.entrySet()) {
            BigDecimal price = dayPrices.get(entry.getKey());
            // Carry forward the prior day's price when the historical source has no observation
            // (weekends, holidays, or a scraper cutoff trailing today). Without this fallback the
            // asset is silently dropped from that day's aggregate, producing a sudden valley in the
            // portfolio chart until the next live snapshot fills it back in — exactly the
            // "yesterday dropped, today normal" pattern users see when running off stale market data.
            if (price == null) {
                PortfolioAssetDailySnapshot prior = priorAssetByKey.get(entry.getKey());
                if (prior != null) price = prior.getUnitPriceTry();
            }
            if (price == null) continue;
            PortfolioPosition first = entry.getValue().get(0);
            BigDecimal totalQty = sumField(entry.getValue(), PortfolioPosition::getQuantity);
            BigDecimal totalCost = sumField(entry.getValue(), PortfolioPosition::entryValue);
            PortfolioAssetDailySnapshot prior = priorAssetByKey.get(entry.getKey());
            PortfolioAssetDailySnapshot snapshot = calculator.buildAggregatedAssetSnapshotWithPrior(
                    portfolioId, first.getAssetType(), first.getAssetCode(), first.getTrackedAsset(),
                    ts, totalQty, totalCost, price, prior);
            batch.add(snapshot);
            dayRows.add(snapshot);
            priorAssetByKey.put(entry.getKey(), snapshot);
        }
    }

    /** On a lot's exit day, emits a pre-close row (valued at exit price) plus a one-second-later zero row marking the holding gone. */
    public void collectClosingSnapshot(Long portfolioId, List<PortfolioPosition> scopedLots,
                                        LocalDate day, List<PortfolioAssetDailySnapshot> batch,
                                        Map<AssetKey, PortfolioAssetDailySnapshot> priorAssetByKey) {
        PortfolioPosition closedOnDay = scopedLots.stream()
                .filter(p -> p.isClosed() && day.equals(p.getExitDate().toLocalDate()))
                .findFirst().orElse(null);
        if (closedOnDay == null) return;
        BigDecimal exitPrice = closedOnDay.getExitPrice() != null ? closedOnDay.getExitPrice() : BigDecimal.ZERO;
        BigDecimal qty = closedOnDay.getQuantity();
        BigDecimal entryValue = closedOnDay.entryValue();
        LocalDateTime preCloseTs = day.atStartOfDay();
        LocalDateTime postCloseTs = preCloseTs.plusSeconds(1);
        PortfolioAssetDailySnapshot prior = priorAssetByKey.get(closedOnDay.toAssetKey());
        PortfolioAssetDailySnapshot preClose = calculator.buildAggregatedAssetSnapshotWithPrior(
                portfolioId, closedOnDay.getAssetType(), closedOnDay.getAssetCode(),
                closedOnDay.getTrackedAsset(), preCloseTs,
                qty, entryValue, exitPrice, prior);
        batch.add(preClose);
        PortfolioAssetDailySnapshot zero = calculator.buildAggregatedAssetSnapshotWithPrior(
                portfolioId, closedOnDay.getAssetType(), closedOnDay.getAssetCode(),
                closedOnDay.getTrackedAsset(), postCloseTs,
                BigDecimal.ZERO, BigDecimal.ZERO, exitPrice, preClose);
        batch.add(zero);
        priorAssetByKey.put(closedOnDay.toAssetKey(), zero);
    }

    public Map<AssetKey, Map<LocalDate, BigDecimal>> loadHistoricalSeries(
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

    public Map<AssetKey, PortfolioAssetDailySnapshot> preloadPriorAssetSnapshots(
            Long portfolioId, List<PortfolioPosition> positions, LocalDate from) {
        LocalDateTime cutoff = from.atStartOfDay();
        Map<Long, AssetKey> keyByTrackedId = new HashMap<>();
        for (PortfolioPosition pos : positions) {
            if (pos.getTrackedAsset() == null) continue;
            keyByTrackedId.putIfAbsent(pos.getTrackedAsset().getId(), pos.toAssetKey());
        }
        if (keyByTrackedId.isEmpty()) return new HashMap<>();
        Map<AssetKey, PortfolioAssetDailySnapshot> result = new HashMap<>();
        for (PortfolioAssetDailySnapshot snap : assetSnapshotRepository
                .findLatestPerTrackedAssetBefore(portfolioId, keyByTrackedId.keySet(), cutoff)) {
            AssetKey key = keyByTrackedId.get(snap.getTrackedAsset().getId());
            if (key != null) result.put(key, snap);
        }
        return result;
    }

    /** Last persisted unit price (in TRY) before today per asset key, used as a fallback when live prices are missing. */
    public Map<AssetKey, BigDecimal> lastKnownPrices(Long portfolioId, Set<AssetKey> keys,
                                                      LocalDate today, Set<AssetKey> existingKeys) {
        Map<AssetKey, BigDecimal> result = new HashMap<>();
        for (AssetKey key : keys) {
            if (existingKeys.contains(key)) continue;
            assetSnapshotRepository
                    .findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                            portfolioId, AssetType.valueOf(key.type().name()),
                            key.assetCode(), today.atStartOfDay())
                    .ifPresent(snap -> {
                        if (snap.getUnitPriceTry() != null) result.put(key, snap.getUnitPriceTry());
                    });
        }
        return result;
    }

    /** Resolves each asset's price for {@code day}: the entry price on its entry day, else the nearest historical price on or before it. */
    public Map<AssetKey, BigDecimal> pricesForDay(List<PortfolioPosition> positions, LocalDate day,
                                                   Map<AssetKey, Map<LocalDate, BigDecimal>> seriesByKey) {
        Map<AssetKey, BigDecimal> result = new HashMap<>();
        for (PortfolioPosition pos : positions) {
            AssetKey key = pos.toAssetKey();
            if (result.containsKey(key)) continue;
            BigDecimal price;
            if (pos.getEntryDate() != null && pos.getEntryDate().toLocalDate().equals(day)) {
                price = pos.getEntryPrice();
            } else {
                price = nearestPriceOnOrBefore(seriesByKey.get(key), day);
            }
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

    public static BigDecimal sumField(List<PortfolioPosition> lots,
                                       Function<PortfolioPosition, BigDecimal> extractor) {
        BigDecimal total = BigDecimal.ZERO;
        for (PortfolioPosition lot : lots) {
            total = total.add(extractor.apply(lot));
        }
        return total;
    }

    public static Map<AssetKey, List<PortfolioPosition>> groupByAsset(List<PortfolioPosition> positions) {
        Map<AssetKey, List<PortfolioPosition>> grouped = new LinkedHashMap<>();
        for (PortfolioPosition pos : positions) {
            grouped.computeIfAbsent(pos.toAssetKey(), k -> new ArrayList<>()).add(pos);
        }
        return grouped;
    }

    /** Lots held on {@code day}: entered on or before it and not yet exited (exit strictly after the day). */
    public static List<PortfolioPosition> activePositionsOn(List<PortfolioPosition> all, LocalDate day) {
        return all.stream()
                .filter(p -> p.getEntryDate() != null && !p.getEntryDate().toLocalDate().isAfter(day))
                .filter(p -> p.getExitDate() == null || p.getExitDate().toLocalDate().isAfter(day))
                .toList();
    }
}
