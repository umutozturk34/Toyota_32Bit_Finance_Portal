package com.finance.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("app.chart")
public record ChartDefaultsProperties(
        Defaults defaults,
        FundDefaults fund,
        Limits limits
) {

    public record Defaults(
            List<IndicatorDefault> indicators,
            String chartType,
            boolean showVolume,
            String magnetMode,
            int iconSize,
            String selectedIcon
    ) {}

    public record FundDefaults(
            String chartType,
            boolean showInvestorCount,
            boolean showPortfolioSize
    ) {}

    public record IndicatorDefault(
            String type,
            int period,
            String color,
            boolean visible
    ) {}

    public record Limits(
            int maxDrawingsPerAsset,
            int maxIndicatorsPerAsset,
            int maxFibToolsPerAsset
    ) {}
}
