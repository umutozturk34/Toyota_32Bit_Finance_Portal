package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.util.CodeNormalizer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class YahooSymbolResolver {

    private static final String YAHOO_FUTURES_SUFFIX = "=F";

    private final Map<String, String> overrides;
    private final Map<String, String> reverseOverrides;

    public YahooSymbolResolver(AppProperties appProperties) {
        this.overrides = Map.copyOf(appProperties.getCommodity().getYahooSymbolOverrides());
        this.reverseOverrides = overrides.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        entry -> entry.getValue().toUpperCase(),
                        Map.Entry::getKey));
    }

    public String resolve(String commodityCode) {
        if (commodityCode == null) return null;
        String override = overrides.get(commodityCode);
        if (override != null) return override;
        if (commodityCode.contains(YAHOO_FUTURES_SUFFIX)) return commodityCode;
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
