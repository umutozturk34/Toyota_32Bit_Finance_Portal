package com.finance.market.commodity.service;

import com.finance.market.commodity.config.CommodityProperties;
import com.finance.shared.util.CodeNormalizer;
import com.finance.market.core.util.YahooSymbolSuffix;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Maps a commodity code to its Yahoo ticker (and back): configured overrides take priority, codes
 * already carrying the Yahoo futures suffix map to themselves, others have no Yahoo symbol.
 */
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

    /** Forward map to a Yahoo ticker; {@code null} means the commodity has no fetchable Yahoo symbol. */
    public String resolve(String commodityCode) {
        if (commodityCode == null) return null;
        String override = overrides.get(commodityCode);
        if (override != null) return override;
        if (commodityCode.contains(YahooSymbolSuffix.FUTURES)) return commodityCode;
        return null;
    }

    /** Reverse map from a Yahoo ticker back to a commodity code; only configured overrides are reversible. */
    public Optional<String> resolveByYahooSymbol(String yahooSymbol) {
        if (yahooSymbol == null || yahooSymbol.isBlank()) return Optional.empty();
        return Optional.ofNullable(reverseOverrides.get(yahooSymbol.trim().toUpperCase()));
    }

    public String normalize(String code) {
        return CodeNormalizer.upper(code);
    }
}
