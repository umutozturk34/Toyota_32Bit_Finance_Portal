package com.finance.user.config;

import com.finance.common.model.TrackedAssetType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bound {@code app.chart.*} configuration defining server-side chart defaults, per-asset-type
 * allow-rules, and limits. Seeds a new user's chart preferences and guards what indicators, drawing
 * tools, and chart types each {@link TrackedAssetType} may use.
 */
@ConfigurationProperties("app.chart")
public record ChartDefaultsProperties(
        Defaults defaults,
        FundDefaults fund,
        Limits limits,
        Map<TrackedAssetType, AssetTypeRules> rules
) {

    /**
     * Baseline chart appearance seeded into a new user's preferences: the starting indicator set,
     * chart type, volume visibility, crosshair magnet mode, and the default drawing-icon styling.
     */
    public record Defaults(
            List<IndicatorDefault> indicators,
            String chartType,
            boolean showVolume,
            String magnetMode,
            int iconSize,
            String selectedIcon
    ) {}

    /**
     * Fund-specific seed defaults that override the generic {@link Defaults} for fund charts, where
     * volume is irrelevant and investor-count / portfolio-size overlays apply instead.
     */
    public record FundDefaults(
            String chartType,
            boolean showInvestorCount,
            boolean showPortfolioSize
    ) {}

    /** A single pre-configured technical indicator (type, look-back period, color, visibility) seeded onto new charts. */
    public record IndicatorDefault(
            String type,
            int period,
            String color,
            boolean visible
    ) {}

    /** Per-asset upper bounds enforced server-side on how many drawings, indicators, and Fibonacci tools a chart may carry. */
    public record Limits(
            int maxDrawingsPerAsset,
            int maxIndicatorsPerAsset,
            int maxFibToolsPerAsset
    ) {}

    /**
     * Allow-list of capabilities permitted for one {@link TrackedAssetType}: the chart types, overlays,
     * indicators, Fibonacci tools, and drawing types that asset class may use. Anything absent is denied.
     */
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
