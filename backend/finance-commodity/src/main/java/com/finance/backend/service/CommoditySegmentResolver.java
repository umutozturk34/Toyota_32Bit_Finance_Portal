package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.model.CommoditySegment;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class CommoditySegmentResolver {

    private static final String YAHOO_FUTURES_SUFFIX = "=F";

    private final Set<String> preciousMetalCodes;

    public CommoditySegmentResolver(AppProperties appProperties) {
        AppProperties.Commodity commodity = appProperties.getCommodity();
        Set<String> keys = new HashSet<>(commodity.getYahooSymbolOverrides().keySet());
        commodity.getDerivatives().forEach(rule -> keys.add(rule.getDerivativeCode()));
        this.preciousMetalCodes = Set.copyOf(keys);
    }

    public CommoditySegment resolve(String commodityCode) {
        if (commodityCode == null) return null;
        if (preciousMetalCodes.contains(commodityCode)) return CommoditySegment.PRECIOUS_METAL;
        if (commodityCode.contains(YAHOO_FUTURES_SUFFIX)) return CommoditySegment.OTHER;
        return null;
    }
}
