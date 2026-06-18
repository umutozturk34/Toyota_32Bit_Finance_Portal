package com.finance.portfolio.service;
import com.finance.shared.event.EventPublisherPort;
import com.finance.common.event.PortfolioUpdatedEvent;
import com.finance.shared.service.PortfolioSnapshotPort;
import com.finance.portfolio.model.PortfolioDailySnapshot;
import org.springframework.beans.factory.ObjectProvider;

import com.finance.portfolio.repository.PortfolioRepository;

import com.finance.portfolio.repository.PortfolioPositionRepository;

import com.finance.portfolio.model.PortfolioPosition;

import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;

import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;

import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.Portfolio;

import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;

import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioType;
import com.finance.portfolio.fixedincome.FixedIncomeSummaryService;
import com.finance.portfolio.dto.response.FixedIncomeSummaryResponse;


import com.finance.shared.util.BatchLogHelper;
import com.finance.shared.util.BatchUpdateRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Produces the periodic portfolio snapshots. Reacts to market-data updates (per asset type, VIOP
 * routed to derivative valuation) by writing per-asset rows and refreshing the day's aggregate, and
 * runs scheduled full daily snapshots over all portfolios. Each portfolio is processed in its own
 * transaction so one failure does not abort the batch; a {@code PortfolioUpdatedEvent} is published
 * after a daily run.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioSnapshotService implements PortfolioSnapshotPort {

    private final SnapshotCalculationService calculator;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final DerivativePositionRepository derivativePositionRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final PortfolioDailySnapshotRepository dailySnapshotRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectProvider<EventPublisherPort> events;
    private final FixedIncomeSummaryService fixedIncomeSummaryService;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Override
    public void onMarketUpdate(MarketType marketType) {
        AssetType type = AssetType.fromMarketType(marketType);
        // Skip asset classes that snapshot through their own pipeline rather than spot PortfolioPosition rows.
        // DEPOSIT/BOND now map to a non-null AssetType but have NO TrackedAssetType peer, so the later
        // TrackedAssetType.valueOf(type.name()) would throw IllegalArgumentException; VIOP also has no spot rows
        // but routes to its own derivative valuation below, so it must NOT be skipped here.
        if (type == null
                || (type != AssetType.VIOP && type.trackedAssetType() == null)) {
            return;
        }
        // A spot market tick (STOCK/CRYPTO/FOREX/VIOP/…) cannot change a FIXED (deposit/bond) portfolio's value, and
        // the spot aggregate path below has no fixed-income branch — running it for a FIXED portfolio would write a
        // 0/empty daily row and DELETE the correct fixed-income snapshot the daily scheduler produced
        // (generateFixedIncomeSnapshot). So exclude FIXED here; it is snapshotted only on the daily schedule.
        List<Portfolio> portfolios = portfolioRepository.findAll().stream()
                .filter(p -> p.getType() != PortfolioType.FIXED)
                .toList();
        LocalDateTime batchTimestamp = LocalDateTime.now();

        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                portfolios,
                portfolio -> transactionTemplate.executeWithoutResult(status -> {
                    if (type == AssetType.VIOP) {
                        snapshotDerivativePositions(portfolio, batchTimestamp);
                    } else {
                        boolean hasPositions = !positionRepository
                                .findByPortfolioIdAndTrackedAsset_AssetTypeAndQuantityGreaterThan(
                                        portfolio.getId(), TrackedAssetType.valueOf(type.name()), BigDecimal.ZERO)
                                .isEmpty();
                        if (hasPositions) {
                            insertAssetSnapshots(portfolio, type, batchTimestamp);
                        }
                    }
                    insertAggregateSnapshot(portfolio, batchTimestamp);
                }),
                p -> String.valueOf(p.getId()),
                "portfolio-" + type + "-snapshot",
                1,
                (p, e) -> log.error("Portfolio {} snapshot failed for portfolioId={}: {}",
                        type, p.getId(), e.getMessage(), e),
                null, null
        );

        BatchLogHelper.logSummary(log, type + " portfolio snapshot", result);
    }

    private void snapshotDerivativePositions(Portfolio portfolio, LocalDateTime batchTimestamp) {
        List<DerivativePosition> positions = derivativePositionRepository
                .findByPortfolioId(portfolio.getId());
        if (positions.isEmpty()) {
            return;
        }
        // Delete-then-insert: every market-data tick re-snapshots VIOP, otherwise each tick
        // appends a new row and today's per-asset table grows by N rows per derivative per day.
        assetSnapshotRepository.deleteByPortfolioIdAndAssetTypeAndSnapshotDate(
                portfolio.getId(), AssetType.VIOP, batchTimestamp.toLocalDate());
        LocalDate today = batchTimestamp.toLocalDate();
        for (DerivativePosition position : positions) {
            PortfolioAssetDailySnapshot snapshot;
            if (position.getCloseDate() != null) {
                // Closed BEFORE today: frozen, write NO row — its proceeds are already in totalValue via
                // DerivativeSnapshotCalculator.addClosedEquity, and a countable row would double-count them.
                if (position.getCloseDate().isBefore(today)) {
                    continue;
                }
                // Closed TODAY: write a VALUE-LESS row (quantity/market/pnl = 0) carrying ONLY the close-day
                // dailyPnl, so the Günlük K/Z card and per-symbol series book today's move. isCountableViopRow=false
                // keeps it out of the value rowMv path, so the proceeds are still counted exactly once via
                // addClosedEquity (no double-count) — the qty>0 row that would double is never written.
                snapshot = calculator.buildClosedViopDailyRow(portfolio.getId(), position, batchTimestamp);
            } else {
                snapshot = calculator.buildDerivativeAssetSnapshot(portfolio.getId(), position, batchTimestamp);
            }
            if (snapshot != null) {
                assetSnapshotRepository.save(snapshot);
            }
        }
    }

    /** Writes a full per-asset + aggregate snapshot for every portfolio; {@code source} labels the trigger (e.g. morning/evening). */
    public void generateDailySnapshots(String source) {
        List<Portfolio> portfolios = portfolioRepository.findAll();

        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                portfolios,
                portfolio -> transactionTemplate.executeWithoutResult(status -> {
                    generateFullSnapshot(portfolio);
                }),
                p -> String.valueOf(p.getId()),
                "daily-snapshot",
                1,
                (p, e) -> log.error("Daily portfolio snapshot failed for portfolioId={} (source={}): {}",
                        p.getId(), source, e.getMessage(), e),
                null, null
        );

        if (!portfolios.isEmpty()) {
            events.ifAvailable(port -> port.publish(PortfolioUpdatedEvent.of(source)));
        }

        BatchLogHelper.logSummary(log, source + " portfolio snapshot", result);
    }

    private void insertAssetSnapshots(Portfolio portfolio, AssetType assetType,
                                       LocalDateTime batchTimestamp) {
        Long pid = portfolio.getId();
        LocalDate today = batchTimestamp.toLocalDate();
        // Open lots PLUS lots closed TODAY: a lot sold today still moved today, so its close-day price move
        // must reach the per-asset row (the daily card reads 0 otherwise on a full sell). buildSymbolRow keeps
        // a sold lot out of market value (realized exit is folded by the aggregate), so this can't double-count.
        List<PortfolioPosition> relevant = positionRepository
                .findByPortfolioIdAndTrackedAsset_AssetTypeAndQuantityGreaterThan(
                        pid, TrackedAssetType.valueOf(assetType.name()), BigDecimal.ZERO)
                .stream()
                .filter(p -> !p.isClosed()
                        || (p.getExitDate() != null && p.getExitDate().toLocalDate().equals(today)))
                .toList();
        // Delete-then-insert per assetType: market-update ticks re-snapshot a single asset
        // type at a time, so wiping by type avoids stomping on other types' rows that may
        // have been written in a different tick today.
        assetSnapshotRepository.deleteByPortfolioIdAndAssetTypeAndSnapshotDate(
                pid, assetType, batchTimestamp.toLocalDate());
        calculator.buildAssetSnapshotsForPositions(pid, relevant, batchTimestamp)
                .forEach(assetSnapshotRepository::save);
    }

    private PortfolioDailySnapshot insertAggregateSnapshot(Portfolio portfolio, LocalDateTime batchTimestamp) {
        dailySnapshotRepository.deleteByPortfolioIdAndSnapshotDate(portfolio.getId(), batchTimestamp.toLocalDate());
        return dailySnapshotRepository.save(calculator.buildAggregateSnapshot(portfolio, batchTimestamp));
    }

    private PortfolioDailySnapshot generateFullSnapshot(Portfolio portfolio) {
        // FIXED (deposit/bond) portfolios have NO spot/derivative rows, so the spot path below would always
        // return null and they'd never get a daily snapshot — silently dropping them from the morning/evening
        // digest. Route them through their own dedicated summary instead, writing the SAME aggregate row so the
        // existing notification reader (and the performance charts) pick them up with no downstream change.
        if (portfolio.getType() == PortfolioType.FIXED) {
            return generateFixedIncomeSnapshot(portfolio);
        }
        Long pid = portfolio.getId();
        LocalDateTime batchTimestamp = LocalDateTime.now();
        LocalDate today = batchTimestamp.toLocalDate();
        // Open lots plus lots closed TODAY, so a sold-today lot's close-day move still reaches its per-asset
        // row (see insertAssetSnapshots); buildSymbolRow keeps it out of market value, so no double-count.
        List<PortfolioPosition> spotRelevant = positionRepository
                .findByPortfolioIdAndQuantityGreaterThan(pid, BigDecimal.ZERO)
                .stream()
                .filter(p -> !p.isClosed()
                        || (p.getExitDate() != null && p.getExitDate().toLocalDate().equals(today)))
                .toList();
        List<DerivativePosition> derivatives = derivativePositionRepository.findByPortfolioId(pid);

        if (spotRelevant.isEmpty() && derivatives.isEmpty()) return null;

        // Full-portfolio snapshot: wipe today's rows across ALL asset types and re-build, so
        // morning + evening schedulers (and any other full-snapshot trigger) replace rather
        // than append. insertAggregateSnapshot does its own delete for the daily row.
        assetSnapshotRepository.deleteByPortfolioIdAndSnapshotDate(pid, batchTimestamp.toLocalDate());
        calculator.buildAssetSnapshotsForPositions(pid, spotRelevant, batchTimestamp)
                .forEach(assetSnapshotRepository::save);
        for (DerivativePosition dpos : derivatives) {
            PortfolioAssetDailySnapshot snap;
            if (dpos.getCloseDate() != null) {
                // Closed BEFORE today: frozen, no row (proceeds in totalValue via addClosedEquity). Closed TODAY:
                // a value-less row carrying ONLY the close-day dailyPnl (see snapshotDerivativePositions).
                if (dpos.getCloseDate().isBefore(today)) {
                    continue;
                }
                snap = calculator.buildClosedViopDailyRow(pid, dpos, batchTimestamp);
            } else {
                snap = calculator.buildDerivativeAssetSnapshot(pid, dpos, batchTimestamp);
            }
            if (snap != null) assetSnapshotRepository.save(snap);
        }

        return insertAggregateSnapshot(portfolio, batchTimestamp);
    }

    /**
     * Daily aggregate snapshot for a FIXED (deposit + Türkiye Hazine bond) portfolio, built from
     * {@link FixedIncomeSummaryService#summary} rather than the spot calculator. Writes into the SAME
     * {@code portfolio_daily_snapshots} table (cash 0; daily P/L = today's value − the latest prior day's value)
     * so the morning/evening notification digest and the performance charts treat it like any other portfolio.
     * An empty fixed-income book (no deposits/bonds) snapshots nothing, mirroring the empty spot case.
     */
    private PortfolioDailySnapshot generateFixedIncomeSnapshot(Portfolio portfolio) {
        Long pid = portfolio.getId();
        LocalDate today = LocalDate.now();
        FixedIncomeSummaryResponse summary = fixedIncomeSummaryService.summary(pid, portfolio.getUserSub());
        // Match the live headline EXACTLY: it folds the received coupon cash into the holder's total value and K/Z
        // (the bond clean-price value alone is only the mark-to-market leg). Snapshotting the clean total would make
        // the daily digest and history diverge from what the user sees on the card, so add the coupons in here too.
        BigDecimal coupons = nz(summary.bondCouponsReceivedTry());
        BigDecimal totalValue = nz(summary.totalValueTry()).add(coupons);
        BigDecimal totalCost = nz(summary.totalCostTry());
        if (totalValue.signum() == 0 && totalCost.signum() == 0) {
            return null;
        }
        BigDecimal totalPnl = nz(summary.totalPnlTry()).add(coupons);
        BigDecimal pnlPercent = totalCost.signum() == 0
                ? null
                : totalPnl.multiply(HUNDRED).divide(totalCost, 4, RoundingMode.HALF_UP);

        BigDecimal priorValue = priorValueBeforeToday(pid, today);
        BigDecimal dailyPnl = priorValue == null ? BigDecimal.ZERO : totalValue.subtract(priorValue);
        BigDecimal dailyPnlPercent = (priorValue == null || priorValue.signum() == 0)
                ? null
                : dailyPnl.multiply(HUNDRED).divide(priorValue, 4, RoundingMode.HALF_UP);

        PortfolioDailySnapshot snapshot = PortfolioDailySnapshot.builder()
                .portfolioId(pid)
                .snapshotDate(today)
                .totalValueTry(totalValue)
                .cashTry(BigDecimal.ZERO)
                .totalCostTry(totalCost)
                .totalPnlTry(totalPnl)
                .pnlPercent(pnlPercent)
                .dailyPnlTry(dailyPnl)
                .dailyPnlPercent(dailyPnlPercent)
                .version(0L)
                .build();
        dailySnapshotRepository.deleteByPortfolioIdAndSnapshotDate(pid, today);
        return dailySnapshotRepository.save(snapshot);
    }

    /** Total value of the most recent snapshot strictly before {@code today} (yesterday's, typically), or null. */
    private BigDecimal priorValueBeforeToday(Long pid, LocalDate today) {
        return dailySnapshotRepository.findRecentByPortfolioId(pid, PageRequest.of(0, 5)).stream()
                .filter(s -> s.getSnapshotDate() != null && s.getSnapshotDate().isBefore(today))
                .map(PortfolioDailySnapshot::getTotalValueTry)
                .findFirst()
                .orElse(null);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
