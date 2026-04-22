package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class YahooSymbolResolver {

    private static final String YAHOO_FUTURES_SUFFIX = "=F";

    private final Map<String, String> overrides;

    public YahooSymbolResolver(AppProperties appProperties) {
        this.overrides = Map.copyOf(appProperties.getCommodity().getYahooSymbolOverrides());
    }

    public String resolve(String commodityCode) {
        if (commodityCode == null) return null;
        String override = overrides.get(commodityCode);
        if (override != null) return override;
        if (commodityCode.contains(YAHOO_FUTURES_SUFFIX)) return commodityCode;
        return null;
    }

    public String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}
