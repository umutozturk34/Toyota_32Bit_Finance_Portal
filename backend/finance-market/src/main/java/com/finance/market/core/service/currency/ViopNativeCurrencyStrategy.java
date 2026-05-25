package com.finance.market.core.service.currency;

import com.finance.common.model.Currency;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.NativeCurrencyStrategy;
import com.finance.market.viop.repository.ViopContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ViopNativeCurrencyStrategy implements NativeCurrencyStrategy {

    private final ViopContractRepository viopContractRepository;

    @Override
    public Set<MarketType> supports() {
        return EnumSet.of(MarketType.VIOP);
    }

    @Override
    public Currency resolve(String code) {
        if (code == null || code.isBlank()) return Currency.TRY;
        Currency fromContract = viopContractRepository.findBySymbol(code)
                .map(c -> Currency.fromCode(c.getCurrency()))
                .filter(Objects::nonNull)
                .orElse(null);
        if (fromContract != null && fromContract != Currency.TRY) return fromContract;
        Currency fromSuffix = currencyFromSuffix(code);
        if (fromSuffix != null) return fromSuffix;
        return fromContract != null ? fromContract : Currency.TRY;
    }

    private static Currency currencyFromSuffix(String code) {
        String upper = code.toUpperCase();
        if (upper.endsWith("USD")) return Currency.USD;
        if (upper.endsWith("EUR")) return Currency.EUR;
        return null;
    }
}
