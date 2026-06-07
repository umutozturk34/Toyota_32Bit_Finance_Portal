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
     * Daily cumulative-return index over {@code [from, to]}: {@code 100 × (1 + cumulativeReturn)} where the
     * cumulative return is the stored {@link PortfolioDailySnapshot#getPnlPercent()} (capital-weighted P&amp;L
     * over cost basis — the SAME figure the portfolio's own headline shows). The compare chart normalizes it
     * by ratio, so its plotted % equals the portfolio's real return from the window start.
     *
     * <p>This deliberately does NOT chain {@code dailyPnlPercent} into a time-weighted-return index anymore.
     * TWR equal-weights each day's return irrespective of the capital at risk, so a book that was tiny-and-up
     * then large-and-down reads as a gain while the investor lost money: a real portfolio that started at
     * ₺20 of spot (up ~65%) then added a ₺606K gold-futures lot that lost ₺65.5K showed a TWR index of
     * +30% against an actual −9.5% (−₺65,530) loss — the same magnitude, opposite sign, surfaced on the
     * compare graph. No base/weighting tweak fixes that; it is inherent to TWR. The capital-weighted return
     * matches the portfolio page and is what the user means by "the portfolio's return". Adding a lot dilutes
     * the percent (return on a larger cost), which is truthful — the curve ends where the portfolio actually is.
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
        List<HistoryPoint> out = new ArrayList<>(snapshots.size());
        for (PortfolioDailySnapshot s : snapshots) {
            if (s.getSnapshotDate() == null) continue;
            BigDecimal cumulativeReturnPct = s.getPnlPercent() != null ? s.getPnlPercent() : BigDecimal.ZERO;
            BigDecimal index = HUNDRED.add(cumulativeReturnPct);
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
