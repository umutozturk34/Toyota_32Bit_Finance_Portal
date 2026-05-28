package com.finance.market.core.service.currency;

import com.finance.common.model.Currency;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.NativeCurrencyStrategy;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Forex native currency: TRY, since forex assets are stored as X/TRY rates quoted in TRY.
 */
@Component
public class ForexNativeCurrencyStrategy implements NativeCurrencyStrategy {

    @Override
    public Set<MarketType> supports() {
        return EnumSet.of(MarketType.FOREX);
    }

    @Override
    public Currency resolve(String code) {
        return Currency.TRY;
    }
}
