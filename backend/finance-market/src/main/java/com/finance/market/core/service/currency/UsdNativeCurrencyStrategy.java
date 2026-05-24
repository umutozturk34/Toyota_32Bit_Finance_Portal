package com.finance.market.core.service.currency;

import com.finance.common.model.Currency;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.NativeCurrencyStrategy;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class UsdNativeCurrencyStrategy implements NativeCurrencyStrategy {

    private static final Set<String> TRY_QUOTED_CRYPTO_IDS = Set.of("tether");
    private static final Set<String> TRY_QUOTED_CRYPTO_TICKERS = Set.of("USDT");

    @Override
    public Set<MarketType> supports() {
        return EnumSet.of(MarketType.CRYPTO);
    }

    @Override
    public Currency resolve(String code) {
        if (code == null) return Currency.USD;
        if (TRY_QUOTED_CRYPTO_IDS.contains(code.toLowerCase())) return Currency.TRY;
        if (TRY_QUOTED_CRYPTO_TICKERS.contains(code.toUpperCase())) return Currency.TRY;
        return Currency.USD;
    }
}
