package com.finance.market.core.service.currency;

import com.finance.common.model.Currency;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.NativeCurrencyStrategy;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class TryNativeCurrencyStrategy implements NativeCurrencyStrategy {

    @Override
    public Set<MarketType> supports() {
        return EnumSet.of(MarketType.STOCK, MarketType.FUND, MarketType.BOND);
    }

    @Override
    public Currency resolve(String code) {
        return Currency.TRY;
    }
}
