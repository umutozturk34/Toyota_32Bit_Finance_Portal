package com.finance.market.core.service.currency;

import com.finance.common.model.Currency;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.NativeCurrencyStrategy;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class DepositNativeCurrencyStrategy implements NativeCurrencyStrategy {

    private static final String DEPOSIT_PREFIX = "TP.";

    @Override
    public Set<MarketType> supports() {
        return EnumSet.of(MarketType.MACRO_DEPOSIT);
    }

    @Override
    public Currency resolve(String code) {
        if (code == null || code.isBlank() || !code.startsWith(DEPOSIT_PREFIX)) {
            return Currency.TRY;
        }
        String payload = code.substring(DEPOSIT_PREFIX.length());
        if (payload.length() < 3) {
            return Currency.TRY;
        }
        String prefix = payload.substring(0, 3).toUpperCase();
        Currency parsed = Currency.fromCode(prefix);
        return parsed != null ? parsed : Currency.TRY;
    }
}
