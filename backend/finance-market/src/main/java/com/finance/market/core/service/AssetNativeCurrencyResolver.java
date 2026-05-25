package com.finance.market.core.service;

import com.finance.common.model.Currency;
import com.finance.common.model.MarketType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public interface AssetNativeCurrencyResolver {

    Currency resolveNativeCurrency(MarketType type, String code);

    @Component
    @RequiredArgsConstructor
    class Default implements AssetNativeCurrencyResolver {

        private final List<NativeCurrencyStrategy> strategies;
        private Map<MarketType, NativeCurrencyStrategy> registry;

        @Override
        public Currency resolveNativeCurrency(MarketType type, String code) {
            if (type == null) {
                return Currency.TRY;
            }
            NativeCurrencyStrategy strategy = registry().get(type);
            return strategy != null ? strategy.resolve(code) : Currency.TRY;
        }

        private Map<MarketType, NativeCurrencyStrategy> registry() {
            Map<MarketType, NativeCurrencyStrategy> snapshot = registry;
            if (snapshot != null) return snapshot;
            EnumMap<MarketType, NativeCurrencyStrategy> built = new EnumMap<>(MarketType.class);
            for (NativeCurrencyStrategy strategy : strategies) {
                for (MarketType type : strategy.supports()) {
                    built.put(type, strategy);
                }
            }
            this.registry = built;
            return built;
        }
    }
}
