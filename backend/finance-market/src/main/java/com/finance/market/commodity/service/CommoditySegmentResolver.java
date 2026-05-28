package com.finance.market.commodity.service;

import com.finance.market.commodity.config.CommodityProperties;
import com.finance.market.commodity.model.CommoditySegment;
import com.finance.market.core.util.YahooSymbolSuffix;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Classifies a commodity into a {@link CommoditySegment}: codes with Yahoo overrides or derivative
 * rules are precious metals, plain Yahoo futures are OTHER, everything else is unclassified.
 */
@Component
public class CommoditySegmentResolver {

    private final Set<String> preciousMetalCodes;

    public CommoditySegmentResolver(CommodityProperties commodityProperties) {
        Set<String> keys = new HashSet<>(commodityProperties.getYahooSymbolOverrides().keySet());
        commodityProperties.getDerivatives().forEach(rule -> keys.add(rule.getDerivativeCode()));
        this.preciousMetalCodes = Set.copyOf(keys);
    }

    public CommoditySegment resolve(String commodityCode) {
        if (commodityCode == null) return null;
        if (preciousMetalCodes.contains(commodityCode)) return CommoditySegment.PRECIOUS_METAL;
        if (commodityCode.contains(YahooSymbolSuffix.FUTURES)) return CommoditySegment.OTHER;
        return null;
    }
}
