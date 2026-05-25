package com.finance.market.core.service.currency;

import com.finance.common.model.Currency;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.NativeCurrencyStrategy;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class CommodityNativeCurrencyStrategy implements NativeCurrencyStrategy {

    @Override
    public Set<MarketType> supports() {
        return EnumSet.of(MarketType.COMMODITY);
    }

    @Override
    public Currency resolve(String code) {
        if (code == null || code.isBlank()) return Currency.USD;
        String upper = code.toUpperCase();
        if (upper.endsWith("TRY") || upper.endsWith("TRYG")) return Currency.TRY;
        if (upper.endsWith("EUR") || upper.endsWith("EURG")) return Currency.EUR;
        return Currency.USD;
    }
}
