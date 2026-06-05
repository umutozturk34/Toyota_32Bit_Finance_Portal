package com.finance.portfolio.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.shared.service.AssetPricingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Assembles a single derivative {@link PortfolioAssetDailySnapshot} in TRY for one position at a
 * timestamp. Market value = exitPrice × fxRate × contractSize × lots, where fxRate is the override
 * (when supplied), else the day's historical FOREX rate (30-day lookback + closest-prior), else the
 * live rate as a last resort. PnL uses {@link com.finance.portfolio.derivative.model.DerivativeDirection};
 * the daily delta is computed against the prior snapshot's unit price.
 */
@Log4j2
@Component
@RequiredArgsConstructor
class DerivativeSnapshotAssembler {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int FX_LOOKBACK_DAYS = 30;

    private final AssetPricingPort pricingPort;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final HistoricalPricingPort historicalPricingPort;

    PortfolioAssetDailySnapshot buildAt(Long portfolioId, DerivativePosition position,
                                         LocalDateTime batchTimestamp, BigDecimal exitPrice,
                                         BigDecimal fxRateOverride, PortfolioAssetDailySnapshot priorOverride) {
        if (position.getViopContract() == null) return null;
        DerivativeMetrics m = computeMetrics(position, exitPrice, fxRateOverride, batchTimestamp.toLocalDate());
        if (m == null) return null;
        DailyDelta delta = resolveDailyDelta(portfolioId, position, m, batchTimestamp, priorOverride);
        return PortfolioAssetDailySnapshot.builder()
                .portfolioId(portfolioId)
                .assetType(AssetType.VIOP)
                .assetCode(position.getViopContract().getSymbol())
                .trackedAsset(null)
                .snapshotDate(batchTimestamp.toLocalDate())
                .createdAt(batchTimestamp)
                .quantity(m.qty())
                .unitPriceTry(m.unitPrice())
                .marketValueTry(m.marketValue())
                .totalCostTry(m.totalCost())
                .pnlTry(m.pnl().setScale(MoneyScale.PRICE, RoundingMode.HALF_UP))
                .dailyPnlTry(delta.amount())
                .dailyPnlPercent(delta.percent())
                .build();
    }

    private DerivativeMetrics computeMetrics(DerivativePosition position, BigDecimal exitPrice,
                                              BigDecimal fxRateOverride, LocalDate snapDate) {
        BigDecimal contractSize = position.getViopContract().getContractSize() != null
                ? position.getViopContract().getContractSize() : BigDecimal.ONE;
        BigDecimal qty = position.getQuantityLot() != null ? position.getQuantityLot() : BigDecimal.ZERO;
        BigDecimal fxRate = fxRateOverride != null && fxRateOverride.signum() > 0
                ? fxRateOverride
                : contractFxRate(position.getViopContract().resolvePriceCurrency(), snapDate);
        if (fxRate == null) {
            // Non-TRY contract with no usable FX rate (historical scraper outage + live cache miss).
            // Persisting native USD/EUR as TRY would corrupt the snapshot row by the FX magnitude
            // (~30x for USD). Returning null upstream forces the caller to skip writing this row;
            // the next scheduler tick can retry once FX recovers.
            return null;
        }
        BigDecimal unitPrice = (exitPrice != null ? exitPrice : BigDecimal.ZERO)
                .multiply(fxRate).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal entryPriceTry = position.getEntryPrice() != null
                ? position.getEntryPrice().setScale(MoneyScale.PRICE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal totalCost = entryPriceTry.multiply(contractSize).multiply(qty)
                .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal perLot = position.getDirection() != null
                ? position.getDirection().pnlPerLot(entryPriceTry, unitPrice, contractSize)
                : null;
        BigDecimal pnl = perLot != null ? perLot.multiply(qty) : BigDecimal.ZERO;
        // Market value = current notional (unitPrice × size × lots) — the position's mark-to-market value.
        // PnL is stored separately and is DIRECTION-AWARE (pnlPerLot): a SHORT profits as the notional
        // falls, so value − cost ≠ PnL for a short, and consumers must read pnlTry, not (value − cost).
        BigDecimal marketValue = unitPrice.multiply(contractSize).multiply(qty)
                .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        return new DerivativeMetrics(qty, contractSize, unitPrice, marketValue, totalCost, pnl);
    }

    private DailyDelta resolveDailyDelta(Long portfolioId, DerivativePosition position,
                                          DerivativeMetrics m, LocalDateTime batchTimestamp,
                                          PortfolioAssetDailySnapshot priorOverride) {
        // ENTRY DAY is contribution-immune: the lot opened today → no prior holding to measure a daily price move
        // against; its whole P&L is an opening contribution, NOT a daily move (a backfill "prior" booked the full
        // lifetime P&L as today's Günlük K/Z — "açtığımda 200"). A CLOSE-DAY row is NOT skipped: a position closed
        // today still MOVED today (prior close → close price), and that move must count in Günlük K/Z exactly as an
        // open position's would ("açık da bugün, kapalı da bugün → etkisi aynı"). So a hedge whose one leg is closed
        // today still nets to ~0 — the open leg's offsetting move is summed alongside it via findLatestRowsPerAsset
        // (the close-day skip was an obsolete workaround from before that multi-row-per-asset sum existed).
        LocalDate snapDate = batchTimestamp.toLocalDate();
        if (position.getEntryDate() != null && position.getEntryDate().equals(snapDate)) return DailyDelta.EMPTY;
        String code = position.getViopContract().getSymbol();
        PortfolioAssetDailySnapshot prior = priorOverride != null
                ? priorOverride
                : assetSnapshotRepository
                        .findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                                portfolioId, AssetType.VIOP, code, batchTimestamp)
                        .orElse(null);
        if (prior == null || prior.getUnitPriceTry() == null) return DailyDelta.EMPTY;
        BigDecimal priorPerLot = position.getDirection() != null
                ? position.getDirection().pnlPerLot(prior.getUnitPriceTry(), m.unitPrice(), m.contractSize())
                : null;
        if (priorPerLot == null) return DailyDelta.EMPTY;
        BigDecimal dailyPnl = priorPerLot.multiply(m.qty()).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal priorValue = prior.getMarketValueTry();
        BigDecimal dailyPercent = priorValue != null && priorValue.compareTo(BigDecimal.ZERO) > 0
                ? dailyPnl.multiply(HUNDRED).divide(priorValue, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : null;
        return new DailyDelta(dailyPnl, dailyPercent);
    }

    /**
     * FX rate to TRY for the price currency on {@code snapDate}: historical first (30-day lookback),
     * live rate as last resort. Returns 1 for TRY contracts (no conversion). Returns null when a
     * non-TRY contract has neither historical nor live FX available — callers must NOT write a
     * snapshot row in that case (the previous behaviour of falling back to 1 silently persisted
     * native USD/EUR as TRY, a ~30x corruption on USD-denominated futures during FX outages).
     */
    private BigDecimal contractFxRate(String currency, LocalDate snapDate) {
        if (currency == null || currency.isBlank() || "TRY".equalsIgnoreCase(currency)) {
            return BigDecimal.ONE;
        }
        String upper = currency.toUpperCase();
        if (snapDate != null) {
            Map<LocalDate, BigDecimal> fxSeries = historicalPricingPort.getPriceSeries(
                    MarketType.FOREX, upper, snapDate.minusDays(FX_LOOKBACK_DAYS), snapDate);
            BigDecimal historicalRate = closestPriorRate(fxSeries, snapDate);
            if (historicalRate != null && historicalRate.signum() > 0) return historicalRate;
        }
        // SELLING rate (getPriceTry), matching the live card, frozen entry cost and the historical path
        // above, so this fallback snapshot row stays on the same FX basis as the card/chart; the
        // exit/buying rate would drift the snapshot off the card by the bid/ask spread.
        BigDecimal liveRate = pricingPort.getPriceTry(MarketType.FOREX, upper);
        if (liveRate != null && liveRate.signum() > 0) return liveRate;
        log.warn("FX rate unavailable currency={} snapDate={} — snapshot row will be skipped", currency, snapDate);
        return null;
    }

    private static BigDecimal closestPriorRate(Map<LocalDate, BigDecimal> series, LocalDate target) {
        if (series == null || series.isEmpty()) return null;
        LocalDate cursor = target;
        for (int i = 0; i <= FX_LOOKBACK_DAYS; i++) {
            BigDecimal rate = series.get(cursor);
            if (rate != null) return rate;
            cursor = cursor.minusDays(1);
        }
        return null;
    }

    private record DerivativeMetrics(BigDecimal qty, BigDecimal contractSize, BigDecimal unitPrice,
                                      BigDecimal marketValue, BigDecimal totalCost, BigDecimal pnl) {}

    private record DailyDelta(BigDecimal amount, BigDecimal percent) {
        static final DailyDelta EMPTY = new DailyDelta(null, null);
    }
}
