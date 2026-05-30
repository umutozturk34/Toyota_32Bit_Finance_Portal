package com.finance.portfolio.service;

import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.portfolio.repository.PortfolioRepository;
import com.finance.shared.service.AssetPricingPort;
import com.finance.shared.service.AssetPricingPort.AssetKey;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recomputes historical snapshots when a lot changes. On a committed {@link LotChangedEvent} it
 * (per-portfolio, striped-lock serialized, in a fresh transaction) wipes affected daily and asset
 * snapshots from the change date, rebuilds per-asset rows (spot only; VIOP rows are maintained by
 * the derivative services) and daily aggregates, then writes today's snapshot. Also exposes manual
 * backfill helpers used to fill gaps in a portfolio's history.
 */
@Log4j2
@Service
public class PortfolioBackfillService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final DerivativePositionRepository derivativePositionRepository;
    private final PortfolioDailySnapshotRepository dailySnapshotRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final AssetPricingPort assetPricingPort;
    private final SnapshotCalculationService calculator;
    private final PortfolioBackfillTracker tracker;
    private final BackfillBatchCollector collector;
    private final TransactionTemplate transactionTemplate;
    private final Object[] portfolioLocks;
    private final int lockStripes;

    public PortfolioBackfillService(PortfolioRepository portfolioRepository,
                                     PortfolioPositionRepository positionRepository,
                                     DerivativePositionRepository derivativePositionRepository,
                                     PortfolioDailySnapshotRepository dailySnapshotRepository,
                                     PortfolioAssetDailySnapshotRepository assetSnapshotRepository,
                                     AssetPricingPort assetPricingPort,
                                     SnapshotCalculationService calculator,
                                     PortfolioBackfillTracker tracker,
                                     BackfillBatchCollector collector,
                                     PlatformTransactionManager transactionManager,
                                     PortfolioProperties portfolioProperties) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.derivativePositionRepository = derivativePositionRepository;
        this.dailySnapshotRepository = dailySnapshotRepository;
        this.assetSnapshotRepository = assetSnapshotRepository;
        this.assetPricingPort = assetPricingPort;
        this.calculator = calculator;
        this.tracker = tracker;
        this.collector = collector;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.lockStripes = portfolioProperties.getBackfill().getLockStripes();
        this.portfolioLocks = new Object[lockStripes];
        for (int i = 0; i < lockStripes; i++) portfolioLocks[i] = new Object();
    }

    private Object lockFor(Long portfolioId) {
        return portfolioLocks[Math.floorMod(portfolioId.hashCode(), lockStripes)];
    }

    /**
     * Signals that a lot of {@code assetCode} changed and history must be recomputed from
     * {@code fromDate}. {@code visibleToUi} drives the backfill progress tracker so the UI can show a
     * pending indicator for user-initiated changes.
     */
    public record LotChangedEvent(Long portfolioId, AssetType assetType,
                                  String assetCode, LocalDate fromDate, boolean visibleToUi) {
    }

    /** Recomputes affected snapshots after a lot change commits; serialized per portfolio, failures are logged not propagated. */
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

    /**
     * Writes today's per-asset and aggregate snapshot using live prices (falling back to the last
     * known snapshot price), covering active spot lots and derivatives opened by today. Skips when
     * live prices are unavailable so the next market update can retry rather than persist zeros.
     */
    public void snapshotToday(Long portfolioId) {
        LocalDate today = LocalDate.now();
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return;

        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        List<PortfolioPosition> active = BackfillBatchCollector.activePositionsOn(positions, today);

        List<AssetKey> keys = active.stream().map(PortfolioPosition::toAssetKey).distinct().toList();
        Map<AssetKey, BigDecimal> dayPrices = keys.isEmpty()
                ? Map.of() : assetPricingPort.getExitPricesTry(keys);
        LocalDateTime ts = LocalDateTime.now();

        if (!active.isEmpty()) {
            Map<AssetKey, List<PortfolioPosition>> byAsset = BackfillBatchCollector.groupByAsset(active);
            Map<AssetKey, BigDecimal> fallbackPrices = collector.lastKnownPrices(portfolioId, byAsset.keySet(), today, Set.of());
            List<PortfolioAssetDailySnapshot> batch = new ArrayList<>();
            for (Map.Entry<AssetKey, List<PortfolioPosition>> entry : byAsset.entrySet()) {
                BigDecimal price = dayPrices.get(entry.getKey());
                if (price == null) price = fallbackPrices.get(entry.getKey());
                if (price == null) continue;
                PortfolioPosition first = entry.getValue().get(0);
                BigDecimal totalQty = BackfillBatchCollector.sumField(entry.getValue(), PortfolioPosition::getQuantity);
                BigDecimal totalCost = BackfillBatchCollector.sumField(entry.getValue(), PortfolioPosition::entryValue);
                batch.add(calculator.buildAggregatedAssetSnapshot(
                        portfolioId, first.getAssetType(), first.getAssetCode(), first.getTrackedAsset(),
                        ts, totalQty, totalCost, price));
            }
            if (!batch.isEmpty()) assetSnapshotRepository.saveAll(batch);
        }

        List<PortfolioPosition> openedByToday = positions.stream()
                .filter(p -> p.getEntryDate() != null && !p.getEntryDate().toLocalDate().isAfter(today))
                .toList();
        List<DerivativePosition> derivatives = derivativePositionRepository.findByPortfolioId(portfolioId);
        List<DerivativePosition> derivativesOpenedByToday = derivatives.stream()
                .filter(d -> d.getEntryDate() != null && !d.getEntryDate().isAfter(today))
                .toList();
        if (openedByToday.isEmpty() && derivativesOpenedByToday.isEmpty()) return;
        if (!active.isEmpty() && dayPrices.isEmpty()) {
            log.info("Skipping snapshotToday for portfolio {} — live prices unavailable, will retry on next market update", portfolioId);
            return;
        }
        List<PortfolioAssetDailySnapshot> derivativeRows = new ArrayList<>();
        for (DerivativePosition dpos : derivativesOpenedByToday) {
            if (dpos.getCloseDate() != null) continue;
            PortfolioAssetDailySnapshot snap = calculator.buildDerivativeAssetSnapshot(portfolioId, dpos, ts);
            if (snap != null) {
                assetSnapshotRepository.save(snap);
                derivativeRows.add(snap);
            }
        }
        List<PortfolioAssetDailySnapshot> todayRows = assetSnapshotRepository
                .findByPortfolioIdAndSnapshotDate(portfolio.getId(), today);
        dailySnapshotRepository.saveAll(List.of(
                calculator.buildAggregateSnapshotAtFromRows(portfolio, ts, openedByToday, derivativesOpenedByToday, dayPrices, todayRows)));
    }

    /**
     * Fills missing per-asset and daily snapshots for every position from {@code from} through
     * yesterday, using historical price series and pre-loaded prior snapshots; days that already have
     * both row types are skipped.
     */
    public void backfillSinceDate(Long portfolioId, LocalDate from) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return;

        LocalDate end = LocalDate.now().minusDays(1);
        if (from == null || from.isAfter(end)) return;

        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        List<DerivativePosition> derivatives = derivativePositionRepository.findByPortfolioId(portfolioId);
        if (positions.isEmpty() && derivatives.isEmpty()) return;

        Set<LocalDate> dailyExisting = Set.copyOf(dailySnapshotRepository.findExistingDates(portfolioId, from, end));
        Set<LocalDate> assetExisting = Set.copyOf(assetSnapshotRepository.findExistingDates(portfolioId, from, end));
        Map<AssetKey, Map<LocalDate, BigDecimal>> seriesByKey = collector.loadHistoricalSeries(positions, from, end);
        Map<AssetKey, PortfolioAssetDailySnapshot> priorAssetByKey = collector.preloadPriorAssetSnapshots(portfolioId, positions, from);

        List<PortfolioAssetDailySnapshot> assetBatch = new ArrayList<>();
        List<PortfolioDailySnapshot> dailyBatch = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(end); day = day.plusDays(1)) {
            collector.collectDay(portfolio, day, positions, derivatives, seriesByKey, dailyExisting, assetExisting,
                    assetBatch, dailyBatch, priorAssetByKey);
        }

        if (!assetBatch.isEmpty()) assetSnapshotRepository.saveAll(assetBatch);
        if (!dailyBatch.isEmpty()) dailySnapshotRepository.saveAll(dailyBatch);
    }

    /**
     * Backfills a portfolio's entire history in a fresh, per-portfolio serialized transaction. Used to
     * seed snapshots for portfolios that entered the database outside the app's lot-change flow — e.g.
     * the clone-and-run demo seed — which therefore never fired a {@link LotChangedEvent}. The open
     * transaction also keeps lazy associations loadable when invoked off the request thread.
     */
    public void backfillEntirePortfolio(Long portfolioId, LocalDate from) {
        if (from == null) return;
        synchronized (lockFor(portfolioId)) {
            transactionTemplate.executeWithoutResult(status -> backfillSinceDate(portfolioId, from));
        }
    }

    /**
     * Rebuilds per-asset snapshots for a single spot asset from {@code from} through today, including
     * a closing (zero) row on the lot's exit day. Aborts (without deleting existing rows) if upstream
     * historical pricing is entirely unavailable for a range that should have history.
     *
     * @throws com.finance.common.exception.BusinessException when upstream pricing is unavailable for an expected-history range
     */
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

        Map<AssetKey, Map<LocalDate, BigDecimal>> seriesByKey = collector.loadHistoricalSeries(scopedLots, from, end);
        boolean allSeriesEmpty = seriesByKey.values().stream().allMatch(s -> s == null || s.isEmpty());
        boolean expectsHistory = !from.equals(end.plusDays(1)) && !from.equals(today);
        if (allSeriesEmpty && expectsHistory) {
            throw new com.finance.common.exception.BusinessException("error.portfolio.backfill.upstreamUnavailable",
                    "Historical pricing unavailable for " + assetType + ":" + assetCode + " — aborting to preserve existing snapshots");
        }
        Map<AssetKey, PortfolioAssetDailySnapshot> priorAssetByKey = collector.preloadPriorAssetSnapshots(portfolioId, scopedLots, from);

        List<PortfolioAssetDailySnapshot> assetBatch = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(end); day = day.plusDays(1)) {
            List<PortfolioPosition> active = BackfillBatchCollector.activePositionsOn(scopedLots, day);
            if (active.isEmpty()) {
                collector.collectClosingSnapshot(portfolioId, scopedLots, day, assetBatch, priorAssetByKey);
                continue;
            }
            if (day.equals(today)) continue;
            Map<AssetKey, BigDecimal> dayPrices = collector.pricesForDay(active, day, seriesByKey);
            LocalDateTime ts = day.atStartOfDay();
            collector.collectAssetSnapshots(portfolioId, active, ts, dayPrices, assetBatch, priorAssetByKey,
                    new ArrayList<>());
        }
        if (!assetBatch.isEmpty()) assetSnapshotRepository.saveAll(assetBatch);
    }

    /** Recomputes daily aggregate snapshots from {@code from} through yesterday off the existing per-asset rows (no price fetch). */
    public void rebuildDailyAggregatesFrom(Long portfolioId, LocalDate from) {
        if (from == null) return;
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return;

        LocalDate end = LocalDate.now().minusDays(1);
        if (from.isAfter(end)) return;

        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        List<DerivativePosition> derivatives = derivativePositionRepository.findByPortfolioId(portfolioId);
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
            List<DerivativePosition> derivativesOpenedByDay = derivatives.stream()
                    .filter(d -> d.getEntryDate() != null && !d.getEntryDate().isAfter(currentDay))
                    .toList();
            if (openedByDay.isEmpty() && derivativesOpenedByDay.isEmpty()) continue;
            List<PortfolioAssetDailySnapshot> dayRows = assetByDay.getOrDefault(currentDay, List.of());
            LocalDateTime ts = currentDay.atStartOfDay();
            dailyBatch.add(calculator.buildAggregateSnapshotAtFromRows(
                    portfolio, ts, openedByDay, derivativesOpenedByDay, Map.of(), dayRows));
        }
        if (!dailyBatch.isEmpty()) dailySnapshotRepository.saveAll(dailyBatch);
    }
}
