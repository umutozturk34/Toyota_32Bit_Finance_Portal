package com.finance.market.core.service.currency;

import com.finance.common.model.Currency;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.NativeCurrencyStrategy;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class MacroIndicatorNativeCurrencyStrategy implements NativeCurrencyStrategy {

    @Override
    public Set<MarketType> supports() {
        return EnumSet.of(MarketType.MACRO_INFLATION, MarketType.MACRO_RATE);
    }

    @Override
    public Currency resolve(String code) {
        return Currency.TRY;
    }
}
