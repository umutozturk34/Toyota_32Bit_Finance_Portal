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

    private static final LocalTime SNAPSHOT_TIME = LocalTime.MIDNIGHT;

    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final com.finance.portfolio.derivative.repository.DerivativePositionRepository derivativePositionRepository;
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
                                     com.finance.portfolio.derivative.repository.DerivativePositionRepository derivativePositionRepository,
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
        this.derivativePositionRepository = derivativePositionRepository;
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
                    boolean isViop = event.assetType() == AssetType.VIOP;
                    wipeDailySnapshotsFrom(portfolioId, event.fromDate());
                    if (!isViop) {
                        wipeAssetSnapshotsForAsset(portfolioId, event.assetType(),
                                event.assetCode(), event.fromDate());
                        backfillAssetSinceDate(portfolioId, event.assetType(),
                                event.assetCode(), event.fromDate());
                    }
                    rebuildDailyAggregatesFrom(portfolioId, event.fromDate());
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

    private void wipeDailySnapshotsFrom(Long portfolioId, LocalDate from) {
        if (from == null) return;
        LocalDate today = LocalDate.now();
        if (from.isAfter(today)) return;
        dailySnapshotRepository.deleteByPortfolioIdAndSnapshotDateBetween(portfolioId, from, today);
    }

    private void wipeAssetSnapshotsForAsset(Long portfolioId, AssetType assetType,
                                            String assetCode, LocalDate from) {
        if (from == null || assetCode == null) return;
        LocalDate today = LocalDate.now();
        if (from.isAfter(today)) return;
        assetSnapshotRepository.deleteByPortfolioIdAndAssetTypeAndAssetCodeAndSnapshotDateBetween(
                portfolioId, assetType, assetCode, from, today);
    }

    private Map<AssetKey, BigDecimal> lastKnownPrices(Long portfolioId,
                                                       java.util.Set<AssetKey> keys,
                                                       LocalDate today,
                                                       Set<AssetKey> existingKeys) {
        Map<AssetKey, BigDecimal> result = new HashMap<>();
        for (AssetKey key : keys) {
            if (existingKeys.contains(key)) continue;
            assetSnapshotRepository
                    .findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                            portfolioId, com.finance.portfolio.model.AssetType.valueOf(key.type().name()),
                            key.assetCode(), today.atStartOfDay())
                    .ifPresent(snap -> {
                        if (snap.getUnitPriceTry() != null) result.put(key, snap.getUnitPriceTry());
                    });
        }
        return result;
    }

    public void snapshotToday(Long portfolioId) {
        LocalDate today = LocalDate.now();
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return;

        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        List<PortfolioPosition> active = activePositionsOn(positions, today);

        List<AssetKey> keys = active.stream().map(PortfolioPosition::toAssetKey).distinct().toList();
        Map<AssetKey, BigDecimal> dayPrices = keys.isEmpty()
                ? Map.of() : assetPricingPort.getExitPricesTry(keys);
        LocalDateTime ts = LocalDateTime.now();

        if (!active.isEmpty()) {
            Map<AssetKey, List<PortfolioPosition>> byAsset = groupByAsset(active);
            Map<AssetKey, BigDecimal> fallbackPrices = lastKnownPrices(portfolioId, byAsset.keySet(), today, Set.of());
            List<PortfolioAssetDailySnapshot> batch = new ArrayList<>();
            for (Map.Entry<AssetKey, List<PortfolioPosition>> entry : byAsset.entrySet()) {
                BigDecimal price = dayPrices.get(entry.getKey());
                if (price == null) price = fallbackPrices.get(entry.getKey());
                if (price == null) continue;
                PortfolioPosition first = entry.getValue().get(0);
                BigDecimal totalQty = sumField(entry.getValue(), PortfolioPosition::getQuantity);
                BigDecimal totalCost = sumField(entry.getValue(), PortfolioPosition::entryValue);
                batch.add(calculator.buildAggregatedAssetSnapshot(
                        portfolioId, first.getAssetType(), first.getAssetCode(), first.getTrackedAsset(),
                        ts, totalQty, totalCost, price));
            }
            if (!batch.isEmpty()) assetSnapshotRepository.saveAll(batch);
        }

        List<PortfolioPosition> openedByToday = positions.stream()
                .filter(p -> p.getEntryDate() != null && !p.getEntryDate().toLocalDate().isAfter(today))
                .toList();
        List<com.finance.portfolio.derivative.model.DerivativePosition> derivatives =
                derivativePositionRepository.findByPortfolioId(portfolioId);
        List<com.finance.portfolio.derivative.model.DerivativePosition> derivativesOpenedByToday = derivatives.stream()
                .filter(d -> d.getEntryDate() != null && !d.getEntryDate().isAfter(today))
                .toList();
        if (openedByToday.isEmpty() && derivativesOpenedByToday.isEmpty()) return;
        if (!active.isEmpty() && dayPrices.isEmpty()) {
            log.info("Skipping snapshotToday for portfolio {} — live prices unavailable, will retry on next market update", portfolioId);
            return;
        }
        List<PortfolioAssetDailySnapshot> todayRows = assetSnapshotRepository
                .findByPortfolioIdAndSnapshotDate(portfolio.getId(), today);
        dailySnapshotRepository.saveAll(List.of(
                calculator.buildAggregateSnapshotAtFromRows(portfolio, ts, openedByToday, derivativesOpenedByToday, dayPrices, todayRows)));
    }

    public void backfillSinceDate(Long portfolioId, LocalDate from) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return;

        LocalDate end = LocalDate.now().minusDays(1);
        if (from == null || from.isAfter(end)) return;

        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        List<com.finance.portfolio.derivative.model.DerivativePosition> derivatives =
                derivativePositionRepository.findByPortfolioId(portfolioId);
        if (positions.isEmpty() && derivatives.isEmpty()) return;

        Set<LocalDate> dailyExisting = Set.copyOf(dailySnapshotRepository.findExistingDates(portfolioId, from, end));
        Set<LocalDate> assetExisting = Set.copyOf(assetSnapshotRepository.findExistingDates(portfolioId, from, end));
        Map<AssetKey, Map<LocalDate, BigDecimal>> seriesByKey = loadHistoricalSeries(positions, from, end);
        Map<AssetKey, PortfolioAssetDailySnapshot> priorAssetByKey = preloadPriorAssetSnapshots(portfolioId, positions, from);

        List<PortfolioAssetDailySnapshot> assetBatch = new ArrayList<>();
        List<PortfolioDailySnapshot> dailyBatch = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(end); day = day.plusDays(1)) {
            collectDay(portfolio, day, positions, derivatives, seriesByKey, dailyExisting, assetExisting,
                    assetBatch, dailyBatch, priorAssetByKey);
        }

        if (!assetBatch.isEmpty()) assetSnapshotRepository.saveAll(assetBatch);
        if (!dailyBatch.isEmpty()) dailySnapshotRepository.saveAll(dailyBatch);
    }

    public void backfillAssetSinceDate(Long portfolioId, AssetType assetType,
                                       String assetCode, LocalDate from) {
        if (from == null || assetCode == null) return;
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return;

        LocalDate today = LocalDate.now();
        LocalDate end = today;
        if (from.isAfter(end)) return;

        List<PortfolioPosition> scopedLots = positionRepository.findByPortfolioId(portfolioId).stream()
                .filter(p -> p.getAssetType() == assetType)
                .filter(p -> assetCode.equalsIgnoreCase(p.getAssetCode()))
                .toList();
        if (scopedLots.isEmpty()) return;

        Map<AssetKey, Map<LocalDate, BigDecimal>> seriesByKey = loadHistoricalSeries(scopedLots, from, end);
        boolean allSeriesEmpty = seriesByKey.values().stream().allMatch(s -> s == null || s.isEmpty());
        boolean expectsHistory = !from.equals(end.plusDays(1)) && !from.equals(today);
        if (allSeriesEmpty && expectsHistory) {
            throw new com.finance.common.exception.BusinessException("error.portfolio.backfill.upstreamUnavailable",
                    "Historical pricing unavailable for " + assetType + ":" + assetCode + " — aborting to preserve existing snapshots");
        }
        Map<AssetKey, PortfolioAssetDailySnapshot> priorAssetByKey = preloadPriorAssetSnapshots(portfolioId, scopedLots, from);

        List<PortfolioAssetDailySnapshot> assetBatch = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(end); day = day.plusDays(1)) {
            List<PortfolioPosition> active = activePositionsOn(scopedLots, day);
            if (active.isEmpty()) {
                collectClosingSnapshot(portfolioId, scopedLots, day, assetBatch, priorAssetByKey);
                continue;
            }
            if (day.equals(today)) continue;
            Map<AssetKey, BigDecimal> dayPrices = pricesForDay(active, day, seriesByKey);
            LocalDateTime ts = day.atTime(SNAPSHOT_TIME);
            collectAssetSnapshots(portfolioId, active, ts, dayPrices, assetBatch, priorAssetByKey,
                    new ArrayList<>());
        }
        if (!assetBatch.isEmpty()) assetSnapshotRepository.saveAll(assetBatch);
    }

    private void collectClosingSnapshot(Long portfolioId, List<PortfolioPosition> scopedLots,
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

    public void rebuildDailyAggregatesFrom(Long portfolioId, LocalDate from) {
        if (from == null) return;
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return;

        LocalDate end = LocalDate.now().minusDays(1);
        if (from.isAfter(end)) return;

        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        List<com.finance.portfolio.derivative.model.DerivativePosition> derivatives =
                derivativePositionRepository.findByPortfolioId(portfolioId);
        if (positions.isEmpty() && derivatives.isEmpty()) return;

        Map<LocalDate, List<PortfolioAssetDailySnapshot>> assetByDay = assetSnapshotRepository
                .findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(portfolioId, from, end)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(PortfolioAssetDailySnapshot::getSnapshotDate));

        List<PortfolioDailySnapshot> dailyBatch = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(end); day = day.plusDays(1)) {
            final LocalDate currentDay = day;
            List<PortfolioPosition> openedByDay = positions.stream()
                    .filter(p -> p.getEntryDate() != null && !p.getEntryDate().toLocalDate().isAfter(currentDay))
                    .toList();
            List<com.finance.portfolio.derivative.model.DerivativePosition> derivativesOpenedByDay = derivatives.stream()
                    .filter(d -> d.getEntryDate() != null && !d.getEntryDate().isAfter(currentDay))
                    .toList();
            if (openedByDay.isEmpty() && derivativesOpenedByDay.isEmpty()) continue;
            List<PortfolioAssetDailySnapshot> dayRows = assetByDay.getOrDefault(currentDay, List.of());
            LocalDateTime ts = currentDay.atTime(SNAPSHOT_TIME);
            dailyBatch.add(calculator.buildAggregateSnapshotAtFromRows(
                    portfolio, ts, openedByDay, derivativesOpenedByDay, Map.of(), dayRows));
        }
        if (!dailyBatch.isEmpty()) dailySnapshotRepository.saveAll(dailyBatch);
    }

    private Map<AssetKey, PortfolioAssetDailySnapshot> preloadPriorAssetSnapshots(
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
                            List<com.finance.portfolio.derivative.model.DerivativePosition> allDerivatives,
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
        List<com.finance.portfolio.derivative.model.DerivativePosition> derivativesOpenedByDay = allDerivatives.stream()
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

    private void collectAssetSnapshots(Long portfolioId, List<PortfolioPosition> active,
                                        LocalDateTime ts, Map<AssetKey, BigDecimal> dayPrices,
                                        List<PortfolioAssetDailySnapshot> batch,
                                        Map<AssetKey, PortfolioAssetDailySnapshot> priorAssetByKey,
                                        List<PortfolioAssetDailySnapshot> dayRows) {
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
            dayRows.add(snapshot);
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
                .filter(p -> p.getExitDate() == null || p.getExitDate().toLocalDate().isAfter(day))
                .toList();
    }

    private Map<AssetKey, BigDecimal> pricesForDay(List<PortfolioPosition> positions, LocalDate day,
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

}
