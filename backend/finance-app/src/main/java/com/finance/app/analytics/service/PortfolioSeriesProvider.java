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

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * Daily time-weighted-return index over {@code [from, to]}, based on the portfolio's real starting value
     * (so the compare tooltip shows a meaningful TRY figure, not a bare "100"), then growing only by each
     * day's contribution-immune price return ({@link PortfolioDailySnapshot#getDailyPnlPercent()}). A lot
     * added on a given day has no prior asset row, so it contributes 0 to that day's factor — deposits stay
     * out of the curve. The result is a value-like growth index: what the starting capital would be worth if
     * it only earned the portfolio's returns. The compare chart normalizes it like any price series, giving a
     * manager-performance line free of cash-flow spikes.
     *
     * @throws ResourceNotFoundException if the portfolio doesn't exist or isn't owned by {@code userSub}
     */
    @Transactional(readOnly = true)
    public List<HistoryPoint> dailyTwrSeries(Long portfolioId, String userSub, LocalDate from, LocalDate to) {
        portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.portfolio.notFound", portfolioId));
        List<PortfolioDailySnapshot> snapshots = dailySnapshotRepository
                .findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(portfolioId, from, to);
        BigDecimal index = null;
        List<HistoryPoint> out = new ArrayList<>(snapshots.size());
        for (PortfolioDailySnapshot s : snapshots) {
            if (s.getSnapshotDate() == null) continue;
            if (index == null) {
                index = s.getTotalValueTry() != null ? s.getTotalValueTry() : HUNDRED;
            } else {
                BigDecimal pct = s.getDailyPnlPercent();
                if (pct != null) {
                    index = index.multiply(BigDecimal.ONE.add(pct.divide(HUNDRED, 10, RoundingMode.HALF_UP)));
                }
            }
            out.add(new HistoryPoint(s.getSnapshotDate(), index.setScale(4, RoundingMode.HALF_UP)));
        }
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
        return snapshots.stream()
                .filter(s -> s.getSnapshotDate() != null && s.getTotalPnlTry() != null)
                .map(s -> new HistoryPoint(s.getSnapshotDate(), s.getTotalPnlTry(), pnlByCcy.get(s.getSnapshotDate())))
                .toList();
    }
}
