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
        // Commodities are always cross-converted to TRY at ingest (PriceCrossCalculator.buildTryCandles),
        // so the stored/native currency is TRY regardless of the code's USD/EUR suffix.
        return Currency.TRY;
    }
}
