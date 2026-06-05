package com.finance.portfolio.service.pricing;

import com.finance.common.model.MarketType;
import com.finance.market.viop.model.ViopCandle;
import com.finance.market.viop.repository.ViopCandleRepository;
import com.finance.shared.service.AssetPricingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Single source of truth for pricing a live derivative (VIOP) leg in TRY. Both the positions-grid
 * formatter and the summary/aggregate valuation paths need the same two primitives — the latest
 * usable candle close and the native-to-TRY conversion — so they live here once instead of being
 * duplicated across callers (which previously risked the two copies drifting apart).
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class DerivativePricingResolver {

    private final ViopCandleRepository viopCandleRepository;
    private final AssetPricingPort pricingPort;

    /**
     * Latest candle close for the symbol, skipping zero/garbage closes (the
     * {@code close > 0} filter). Returns {@code null} when the symbol is unknown or no positive
     * close exists, so callers fall back to the contract last price or an entry-priced value.
     */
    public BigDecimal latestCandleClose(String symbol) {
        if (symbol == null) return null;
        return viopCandleRepository.findFirstBySymbolAndCloseGreaterThanOrderByCandleDateDesc(symbol, BigDecimal.ZERO)
                .map(ViopCandle::getClose)
                .orElse(null);
    }

    /**
     * Converts a native-currency live price into TRY using the FOREX SELLING rate — the same FX
     * field the frozen entry cost and the chart/snapshot rows use, so a same-day open crypto/VIOP
     * reads ~0 instead of a phantom bid/ask spread (card == chart). The exit/buying rate is
     * deliberately not used for live market value.
     *
     * <p>Returns {@code null} (never the native value, never the contract last price) when the FX
     * rate is missing or non-positive: returning the native value as TRY would silently under-count
     * by ~30x (100 USD treated as 100 TRY). Null lets callers fall back to {@code entryNotional.abs()}
     * with PnL held at zero — the correct degraded state until the scraper recovers, identical to a
     * missing live price.
     */
    public BigDecimal convertLiveToTry(BigDecimal nativePrice, String currency) {
        if (nativePrice == null) return null;
        if (currency == null || currency.isBlank() || "TRY".equalsIgnoreCase(currency)) {
            return nativePrice;
        }
        BigDecimal rate = pricingPort.getPriceTry(MarketType.FOREX, currency.toUpperCase());
        if (rate == null || rate.signum() <= 0) {
            log.warn("Live FX missing/non-positive for currency={} rate={} — caller falls back to entry notional, PnL held at 0", currency, rate);
            return null;
        }
        return nativePrice.multiply(rate);
    }
}
