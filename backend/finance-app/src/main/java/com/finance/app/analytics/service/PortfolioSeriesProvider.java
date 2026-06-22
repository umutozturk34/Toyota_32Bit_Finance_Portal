package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.portfolio.model.PortfolioDailySnapshot;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioRepository;
import com.finance.portfolio.service.performance.PortfolioPerformanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exposes a portfolio's daily total-value-in-TRY series (from stored snapshots) so analytics can chart
 * a portfolio alongside scenario instruments. Access is owner-scoped by {@code userSub}.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioSeriesProvider {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioDailySnapshotRepository dailySnapshotRepository;
    private final PortfolioPerformanceService portfolioPerformanceService;

    /**
     * Daily {@code totalValueTry} points over {@code [from, to]} for the owner's portfolio.
     *
     * @throws ResourceNotFoundException if the portfolio doesn't exist or isn't owned by {@code userSub}
     */
    @Transactional(readOnly = true)
    public List<HistoryPoint> dailyValueSeries(Long portfolioId, String userSub, LocalDate from, LocalDate to) {
        portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.portfolio.notFound", portfolioId));
        List<PortfolioDailySnapshot> snapshots = dailySnapshotRepository
                .findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(portfolioId, from, to);
        log.debug("Portfolio series fetched portfolioId={} window={}..{} points={}",
                portfolioId, from, to, snapshots.size());
        return snapshots.stream()
                .filter(s -> s.getSnapshotDate() != null && s.getTotalValueTry() != null)
                .map(s -> new HistoryPoint(s.getSnapshotDate(), s.getTotalValueTry()))
                .toList();
    }

    /**
     * Daily time-weighted-return index over {@code [from, to]}, 100-based — the compare chart's portfolio line.
     * It chains each snapshot's cash-flow-neutral daily return (see
     * {@link PortfolioPerformanceService#twrIndexSeries}) so the return is independent of how much capital was
     * invested when: it answers "did my holdings beat the benchmark / inflation over this window", which is what
     * a benchmark comparison needs. This is deliberately NOT the money-weighted figure the portfolio card shows —
     * that one credits/penalises contribution timing (the user's actual wallet result).
     *
     * @throws ResourceNotFoundException if the portfolio doesn't exist or isn't owned by {@code userSub}
     */
    @Transactional(readOnly = true)
    public List<HistoryPoint> dailyReturnIndexSeries(Long portfolioId, String userSub, LocalDate from, LocalDate to) {
        portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.portfolio.notFound", portfolioId));
        List<PortfolioDailySnapshot> snapshots = dailySnapshotRepository
                .findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(portfolioId, from, to);
        Map<LocalDate, BigDecimal> twr = portfolioPerformanceService.twrIndexSeries(snapshots);
        List<HistoryPoint> out = new ArrayList<>(twr.size());
        twr.forEach((date, index) -> out.add(new HistoryPoint(date, index.setScale(4, RoundingMode.HALF_UP))));
        return out;
    }

    /**
     * Daily cumulative profit/loss in TRY over {@code [from, to]} — the stored {@code totalPnlTry} per
     * snapshot. This is the same quantity the portfolio "Total" Kâr/Zarar line plots, so the compare chart
     * can render the portfolio as a real TRY P&amp;L curve anchored to ₺0 at the window start. Unlike the raw
     * value series it is contribution-immune in level: a lot bought at market adds equal value and cost, so
     * cumulative PnL is unchanged on the entry day and only moves with price thereafter — no deposit spikes.
     *
     * @throws ResourceNotFoundException if the portfolio doesn't exist or isn't owned by {@code userSub}
     */
    @Transactional(readOnly = true)
    public List<HistoryPoint> dailyPnlSeries(Long portfolioId, String userSub, LocalDate from, LocalDate to) {
        portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.portfolio.notFound", portfolioId));
        List<PortfolioDailySnapshot> snapshots = dailySnapshotRepository
                .findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(portfolioId, from, to);
        // Per-currency P&L (USD/EUR, entry-FX cost basis) rides along each point so the compare money overlay
        // shows the true cost-based foreign-currency figure instead of converting the netted TRY P&L at one rate.
        Map<LocalDate, Map<String, BigDecimal>> pnlByCcy =
                portfolioPerformanceService.dailyPnlByCcy(portfolioId, snapshots);
        // Per-currency cumulative RETURN INDEX rides along too, so the compare LINE plots the real foreign-
        // currency return (cost@entry-date FX, value@point-date FX) instead of FX-converting the netted TRY index.
        Map<LocalDate, Map<String, BigDecimal>> returnIndexByCcy =
                portfolioPerformanceService.dailyReturnIndexByCcy(portfolioId, snapshots);
        return snapshots.stream()
                .filter(s -> s.getSnapshotDate() != null && s.getTotalPnlTry() != null)
                .map(s -> new HistoryPoint(s.getSnapshotDate(), s.getTotalPnlTry(),
                        pnlByCcy.get(s.getSnapshotDate()), returnIndexByCcy.get(s.getSnapshotDate())))
                .toList();
    }
}
