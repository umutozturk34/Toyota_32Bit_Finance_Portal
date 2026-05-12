package com.finance.user.config;

import com.finance.common.model.TrackedAssetType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.Set;

@ConfigurationProperties("app.chart")
public record ChartDefaultsProperties(
        Defaults defaults,
        FundDefaults fund,
        Limits limits,
        Map<TrackedAssetType, AssetTypeRules> rules
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

    public record AssetTypeRules(
            Set<String> allowedChartTypes,
            boolean allowVolume,
            boolean allowInvestorCount,
            boolean allowPortfolioSize,
            Set<String> allowedIndicators,
            Set<String> allowedFibTools,
            Set<String> allowedDrawingTypes
    ) {}
}
