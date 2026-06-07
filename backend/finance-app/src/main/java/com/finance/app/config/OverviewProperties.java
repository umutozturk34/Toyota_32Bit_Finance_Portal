package com.finance.app.config;

import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.common.model.MarketType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Configuration for the market-overview dashboard ({@code app.overview}): layout limits, per-user
 * defaults, and per-widget-kind size/item settings used to validate and clamp saved layouts.
 */
@ConfigurationProperties("app.overview")
public record OverviewProperties(
        Limits limits,
        Defaults defaults,
        Map<WidgetKind, WidgetSettings> widgets
) {

    /**
     * Global ceilings enforced when a dashboard layout is saved: total widgets, asset-card widgets,
     * any per-widget config "limit" value, and grid row count.
     */
    public record Limits(
            int maxWidgetsPerLayout,
            int maxAssetCardWidgetsPerLayout,
            int maxConfigLimit,
            int maxLayoutRows
    ) {}

    /**
     * Seed values applied when a user has no saved layout or omits a setting: default item counts for
     * movers/news/watchlist, the starter asset references, and the default widget sections.
     */
    public record Defaults(
            int moverLimit,
            int newsCount,
            int watchlistLimit,
            List<AssetReferenceConfig> assetReferences,
            List<DefaultSectionConfig> sections
    ) {}

    /**
     * Size envelope for one widget kind: default placement size, the allowed min/max bounds used to
     * clamp user-resized widgets, and an optional cap on the number of rendered items.
     */
    public record WidgetSettings(
            Size defaults,
            Size min,
            Size max,
            Integer maxItems
    ) {
        /** Clamps a requested width into the {@code [min.w, max.w]} range for this widget kind. */
        public int clampW(int requested) {
            return Math.max(min.w(), Math.min(max.w(), requested));
        }

        /** Clamps a requested height into the {@code [min.h, max.h]} range for this widget kind. */
        public int clampH(int requested) {
            return Math.max(min.h(), Math.min(max.h(), requested));
        }
    }

    /** A widget's grid footprint in columns ({@code w}) and rows ({@code h}). */
    public record Size(int w, int h) {}

    /** Points the default asset-card widget at a specific instrument by market type and code. */
    public record AssetReferenceConfig(MarketType type, String code) {}

    /**
     * One entry in the out-of-the-box layout: a stable section id, its widget kind, the display order,
     * and the market type it is scoped to (where applicable).
     */
    public record DefaultSectionConfig(String sectionId, WidgetKind kind, int order, MarketType marketType) {}

    /**
     * Settings for a widget kind.
     *
     * @throws IllegalStateException if no settings are configured for the kind (misconfiguration)
     */
    public WidgetSettings settingsFor(WidgetKind kind) {
        WidgetSettings s = widgets.get(kind);
        if (s == null) {
            throw new IllegalStateException("Missing app.overview.widgets entry for kind=" + kind);
        }
        return s;
    }
}
