package com.finance.app.analytics.dto.response;

import com.finance.app.analytics.dto.RiskLevel;

import java.math.BigDecimal;

/**
 * One asset's realized return over a single window. The top-level fields are the TRY view: {@code returnPct}
 * the price growth %, {@code returnTry} the per-unit TRY price change (now − then), with both window-edge
 * prices, the annualized volatility % and its risk band. {@code usd} and {@code eur} carry the SAME window
 * expressed in that currency — every leg converted at its own date's FX (nearest rate on/before, like
 * Compare) so a foreign-currency return reflects the FX move, not just the lira's, and each currency is its
 * own ranking on the frontend. Either is null when FX history doesn't cover the window. Windows the asset's
 * history doesn't cover (within a ±30-day tolerance of the start) are omitted from the row entirely.
 */
public record PeriodReturn(
        BigDecimal returnPct,
        BigDecimal returnTry,
        BigDecimal priceThen,
        BigDecimal priceNow,
        BigDecimal volatility,
        RiskLevel riskLevel,
        CurrencyFigures usd,
        CurrencyFigures eur) {

    /**
     * The same window's figures expressed in a non-TRY currency: {@code returnPct} the growth %,
     * {@code returnValue} the per-unit price change (now − then) in that currency, the two window-edge
     * prices, and the currency-specific annualized volatility % with its risk band.
     */
    public record CurrencyFigures(
            BigDecimal returnPct,
            BigDecimal returnValue,
            BigDecimal priceThen,
            BigDecimal priceNow,
            BigDecimal volatility,
            RiskLevel riskLevel) {
    }
}
