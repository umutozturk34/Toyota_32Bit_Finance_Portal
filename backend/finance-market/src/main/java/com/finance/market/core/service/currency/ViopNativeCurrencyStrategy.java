package com.finance.market.core.service.currency;

import com.finance.common.model.Currency;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.NativeCurrencyStrategy;
import com.finance.market.viop.model.ViopContract;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * VIOP native currency: derived from the contract symbol via {@link ViopContract#quoteCurrencyOf}
 * (options TRY; futures may be USD/EUR), never from the stored exchange-currency field.
 */
@Component
public class ViopNativeCurrencyStrategy implements NativeCurrencyStrategy {

    @Override
    public Set<MarketType> supports() {
        return EnumSet.of(MarketType.VIOP);
    }

    /** Resolves the symbol-derived quote currency, falling back to TRY when unparseable. */
    @Override
    public Currency resolve(String code) {
        Currency quote = Currency.fromCode(ViopContract.quoteCurrencyOf(code));
        return quote != null ? quote : Currency.TRY;
    }
}
