package com.finance.portfolio.service.summary;

import com.finance.portfolio.service.performance.PortfolioPerformanceService;

import com.finance.portfolio.dto.response.CurrencyFramePct;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioDailySnapshot;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Post-processes the headline multi-currency frames so the USD/EUR daily K/Z reflects each leg's
 * native day-move rather than today's single FX rate. Lives alongside the summary service because it
 * is a self-contained correction step the summary applies after computing the base frames.
 */
@Log4j2
@Component
@RequiredArgsConstructor
class PerCurrencyFrameOverrideService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    /** Below one cent a per-date foreign daily delta is FX-rounding residue, not a real move → snap to 0. */
    private static final BigDecimal DAILY_FX_DUST = new BigDecimal("0.01");

    private final PortfolioDailySnapshotRepository portfolioSnapshotRepository;
    private final PortfolioPerformanceService performanceService;

    /**
     * Replace the USD/EUR daily K/Z with the per-date frame DELTA (latest snapshot date − prior date). The
     * frame's own daily is the TRY daily ÷ TODAY's single rate — correct for a TRY-native asset (its TRY daily
     * carries no FX component) but wrong for a USD/EUR-quoted VİOP, whose TRY daily mixes the native price move
     * with the day's FX move; single-rate then contaminates it by a value-scaled FX drift. The per-date delta
     * (each day's value at its OWN FX, cost cancels) expresses every leg in its own currency — a USD-quoted VİOP
     * reads its native USD day-move. TRY is left as the stored daily. No-op with fewer than two snapshot dates.
     */
    Map<String, CurrencyFramePct> withPerDateForeignDaily(Long portfolioId, AssetType filterType,
            Map<String, CurrencyFramePct> frames) {
        List<PortfolioDailySnapshot> recent = portfolioSnapshotRepository
                .findRecentByPortfolioId(portfolioId, PageRequest.of(0, 10));
        if (recent == null || recent.size() < 2) {
            log.debug("Fewer than 2 recent snapshots portfolioId={} — keeping single-rate USD/EUR daily (no per-date override)", portfolioId);
            return frames;
        }
        // Ascending createdAt so the LATEST snapshot of each date wins in the per-date map (dailyPnlByCcy keys by
        // snapshotDate, last write per date wins).
        List<PortfolioDailySnapshot> ascending = new ArrayList<>(recent);
        Collections.reverse(ascending);
        Map<LocalDate, Map<String, BigDecimal>> byDate =
                performanceService.dailyPnlByCcy(portfolioId, ascending, filterType);
        List<LocalDate> dates = byDate.keySet().stream().sorted(Comparator.reverseOrder()).toList();
        if (dates.size() < 2) return frames;
        Map<String, BigDecimal> todayPnl = byDate.get(dates.get(0));
        Map<String, BigDecimal> priorPnl = byDate.get(dates.get(1));
        if (todayPnl == null || priorPnl == null) return frames;
        Map<String, CurrencyFramePct> out = new LinkedHashMap<>(frames);
        for (String ccy : List.of("USD", "EUR")) {
            CurrencyFramePct f = frames.get(ccy);
            BigDecimal t = todayPnl.get(ccy);
            BigDecimal p = priorPnl.get(ccy);
            if (f == null || t == null || p == null) continue;
            BigDecimal daily = t.subtract(p).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
            // Below one cent the foreign daily is FX-rounding residue on a non-moving (e.g. netted-hedge)
            // position, not a real move — snap to 0 so it shows €0,00 not €0,01 dust. Mirrors the headline
            // snap in MultiCurrencyPnlCalculator (DAILY_FX_DUST) so the override doesn't re-introduce the dust.
            if (daily.abs().compareTo(DAILY_FX_DUST) < 0) daily = BigDecimal.ZERO;
            BigDecimal yesterdayValue = f.totalValue() != null ? f.totalValue().subtract(daily) : null;
            BigDecimal dailyPct = (yesterdayValue != null && yesterdayValue.signum() != 0)
                    ? daily.multiply(HUNDRED).divide(yesterdayValue, MoneyScale.PRICE, RoundingMode.HALF_UP)
                    : f.dailyPnlPercent();
            out.put(ccy, new CurrencyFramePct(f.pnlPercent(), dailyPct, f.totalValue(), f.totalEntry(), f.totalPnl(), daily));
        }
        return out;
    }
}
