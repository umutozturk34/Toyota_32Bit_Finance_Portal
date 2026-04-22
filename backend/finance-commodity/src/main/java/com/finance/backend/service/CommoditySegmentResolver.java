package com.finance.backend.service;

import com.finance.backend.config.CommodityProperties;
import com.finance.backend.model.CommoditySegment;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class CommoditySegmentResolver {

    private static final String YAHOO_FUTURES_SUFFIX = "=F";

    private final Set<String> preciousMetalCodes;

    public CommoditySegmentResolver(CommodityProperties commodityProperties) {
        Set<String> keys = new HashSet<>(commodityProperties.getYahooSymbolOverrides().keySet());
        commodityProperties.getDerivatives().forEach(rule -> keys.add(rule.getDerivativeCode()));
        this.preciousMetalCodes = Set.copyOf(keys);
    }

    public CommoditySegment resolve(String commodityCode) {
        if (commodityCode == null) return null;
        if (preciousMetalCodes.contains(commodityCode)) return CommoditySegment.PRECIOUS_METAL;
        if (commodityCode.contains(YAHOO_FUTURES_SUFFIX)) return CommoditySegment.OTHER;
        return null;
    }
}
