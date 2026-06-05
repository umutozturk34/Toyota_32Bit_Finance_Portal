package com.finance.portfolio.service.summary;

import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes the headline daily PnL leg: today's per-asset daily delta summed across still-held assets,
 * with yesterday's value recovered per asset as the daily-% baseline. Falls back to the whole-portfolio
 * snapshot day-over-day diff when no per-asset rows match. Runs inside the caller's read-only transaction.
 */
@Log4j2
@Component
@RequiredArgsConstructor
class DailyAggregationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final PortfolioDailySnapshotRepository portfolioSnapshotRepository;
    private final DerivativePositionRepository derivativePositionRepository;

    /**
     * Asset identities ("TYPE|CODE") whose move counts toward TODAY's daily K/Z — spot lots that are open OR
     * closed TODAY, plus derivatives that are open OR closed TODAY. The daily card must only sum snapshots for
     * assets still relevant today: a deleted/older-closed position leaves stale per-asset daily rows behind, and
     * {@link PortfolioAssetDailySnapshotRepository#findLatestPerAsset} returns the latest row for EVERY asset
     * ever held, so without this gate a fully-liquidated portfolio shows a phantom daily PnL. A position CLOSED
     * TODAY is included because it still moved today (its close-day row carries that move) — so a symbol sold in
     * full today still books its day-move instead of dropping to 0, matching the partial-sell and VİOP cases.
     */
    Set<String> liveOpenAssetKeys(Long portfolioId, List<PortfolioPosition> spotPositions) {
        Set<String> keys = new HashSet<>();
        LocalDate today = LocalDate.now();
        for (PortfolioPosition p : spotPositions) {
            boolean relevantToday = !p.isClosed()
                    || (p.getExitDate() != null && p.getExitDate().toLocalDate().equals(today));
            if (relevantToday && p.getAssetType() != null && p.getAssetCode() != null) {
                keys.add(p.getAssetType().name() + "|" + p.getAssetCode());
            }
        }
        for (DerivativePosition d : derivativePositionRepository.findByPortfolioId(portfolioId)) {
            boolean relevantToday = d.isOpen() || (d.getCloseDate() != null && d.getCloseDate().equals(today));
            if (relevantToday && d.getViopContract() != null && d.getViopContract().getSymbol() != null) {
                keys.add(AssetType.VIOP.name() + "|" + d.getViopContract().getSymbol());
            }
        }
        return keys;
    }

    /**
     * Today's aggregate daily PnL AND yesterday's value (the prior baseline for the daily %), both summed
     * per asset so a lot added to an existing asset doesn't inflate the baseline. Only assets still held
     * ({@code liveKeys}) contribute — a liquidated/closed asset's lingering snapshot rows are skipped.
     */
    DailyAgg aggregateDaily(Long portfolioId, AssetType assetType, BigDecimal totalValue, Set<String> liveKeys) {
        // No live holdings ⇒ nothing moved today; return a hard zero instead of falling back to the
        // whole-portfolio snapshot delta (which on a liquidation day is today 0 − yesterday's value = a
        // phantom loss) or summing stale per-asset rows (a phantom gain).
        if (liveKeys.isEmpty()) {
            log.debug("No live open assets portfolioId={} assetType={} — daily PnL is 0 (fully liquidated/closed)", portfolioId, assetType);
            return new DailyAgg(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        // Every row at each symbol's latest instant — NOT one. A same-symbol multi-lot VİOP position (e.g. a
        // LONG+SHORT hedge) is stored one row per lot at the same createdAt; summing all of them nets the day's
        // move (a balanced hedge → ~0). The single-row findLatestPerAsset returned only one leg, so a net-0
        // hedge's Günlük K/Z read one direction's move (the ₺769 / $16.73 phantom). Since the per-currency
        // daily is just this TRY amount converted (computeFromFootprints), fixing it here fixes USD/EUR too.
        List<PortfolioAssetDailySnapshot> latestPerAsset = assetSnapshotRepository.findLatestRowsPerAsset(portfolioId);
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal baseline = BigDecimal.ZERO;
        boolean any = false;
        for (PortfolioAssetDailySnapshot s : latestPerAsset) {
            if (assetType != null && s.getAssetType() != assetType) continue;
            if (!liveKeys.contains(s.getAssetType().name() + "|" + s.getAssetCode())) continue;
            if (s.getDailyPnlTry() == null) continue;   // new-asset rows (EMPTY delta) have no prior — contribute nothing
            amount = amount.add(s.getDailyPnlTry());
            baseline = baseline.add(priorValueOf(s));
            any = true;
        }
        if (any) {
            return new DailyAgg(amount.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP),
                    baseline.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP));
        }
        // No per-asset rows: fall back to the whole-portfolio snapshot delta. That path tracks no mid-day
        // lot additions, so totalValue - dailyPnl is the exact prior baseline there.
        log.debug("No matching per-asset daily rows portfolioId={} assetType={} — falling back to whole-portfolio snapshot delta", portfolioId, assetType);
        BigDecimal fallback = portfolioSnapshotDailyDelta(portfolioId, assetType);
        BigDecimal fallbackBaseline = (totalValue != null && fallback != null) ? totalValue.subtract(fallback) : null;
        return new DailyAgg(fallback, fallbackBaseline);
    }

    /**
     * Yesterday's value of one asset (priorQty*priorPrice), recovered from its stored daily delta:
     * percent = dailyPnl*100/priorValue ⇒ priorValue = dailyPnl*100/percent.
     */
    static BigDecimal priorValueOf(PortfolioAssetDailySnapshot s) {
        BigDecimal pnl = s.getDailyPnlTry();
        BigDecimal pct = s.getDailyPnlPercent();
        if (pct == null) return BigDecimal.ZERO;                  // prior value was 0/none
        if (pct.signum() == 0) {                                  // flat price: priorValue == marketValue (dailyPnl≈0)
            return s.getMarketValueTry() != null ? s.getMarketValueTry().subtract(pnl) : BigDecimal.ZERO;
        }
        return pnl.multiply(HUNDRED).divide(pct, MoneyScale.PRICE, RoundingMode.HALF_UP);
    }

    private BigDecimal portfolioSnapshotDailyDelta(Long portfolioId, AssetType assetType) {
        if (assetType != null) return null;
        List<PortfolioDailySnapshot> recent = portfolioSnapshotRepository
                .findRecentByPortfolioId(portfolioId, PageRequest.of(0, 2));
        if (recent.size() < 2) return null;
        BigDecimal latest = recent.get(0).getTotalValueTry();
        BigDecimal prior = recent.get(1).getTotalValueTry();
        if (latest == null || prior == null) return null;
        return latest.subtract(prior).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
    }
}
