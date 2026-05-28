package com.finance.market.core.service.currency;

import com.finance.common.model.Currency;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.NativeCurrencyStrategy;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Crypto native currency: USD by default, but tether (USDT) is treated as TRY-quoted since it
 * tracks the dollar and is held as a TRY-side stable leg in this platform.
 */
@Component
public class UsdNativeCurrencyStrategy implements NativeCurrencyStrategy {

    private static final Set<String> TRY_QUOTED_CRYPTO_IDS = Set.of("tether");
    private static final Set<String> TRY_QUOTED_CRYPTO_TICKERS = Set.of("USDT");

    @Override
    public Set<MarketType> supports() {
        return EnumSet.of(MarketType.CRYPTO);
    }

    /** USD for all coins except tether/USDT, which resolve to TRY. */
    @Override
    public Currency resolve(String code) {
        if (code == null) return Currency.USD;
        if (TRY_QUOTED_CRYPTO_IDS.contains(code.toLowerCase())) return Currency.TRY;
        if (TRY_QUOTED_CRYPTO_TICKERS.contains(code.toUpperCase())) return Currency.TRY;
        return Currency.USD;
    }
}
