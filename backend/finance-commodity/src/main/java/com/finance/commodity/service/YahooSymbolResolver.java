package com.finance.commodity.service;

import com.finance.commodity.config.CommodityProperties;
import com.finance.common.util.CodeNormalizer;
import com.finance.common.util.YahooSymbolSuffix;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class YahooSymbolResolver {

    private final Map<String, String> overrides;
    private final Map<String, String> reverseOverrides;

    public YahooSymbolResolver(CommodityProperties commodityProperties) {
        this.overrides = Map.copyOf(commodityProperties.getYahooSymbolOverrides());
        this.reverseOverrides = overrides.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        entry -> entry.getValue().toUpperCase(),
                        Map.Entry::getKey));
    }

    public String resolve(String commodityCode) {
        if (commodityCode == null) return null;
        String override = overrides.get(commodityCode);
        if (override != null) return override;
        if (commodityCode.contains(YahooSymbolSuffix.FUTURES)) return commodityCode;
        return null;
    }

    public Optional<String> resolveByYahooSymbol(String yahooSymbol) {
        if (yahooSymbol == null || yahooSymbol.isBlank()) return Optional.empty();
        return Optional.ofNullable(reverseOverrides.get(yahooSymbol.trim().toUpperCase()));
    }

    public String normalize(String code) {
        return CodeNormalizer.upper(code);
    }
}
