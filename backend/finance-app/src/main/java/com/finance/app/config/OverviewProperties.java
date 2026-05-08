package com.finance.app.config;

import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.common.model.MarketType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties("app.overview")
public record OverviewProperties(
        Limits limits,
        Defaults defaults,
        Map<WidgetKind, WidgetSettings> widgets
) {

    public record Limits(
            int maxWidgetsPerLayout,
            int maxAssetCardWidgetsPerLayout,
            int maxConfigLimit
    ) {}

    public record Defaults(
            int moverLimit,
            int newsCount,
            int watchlistLimit,
            List<AssetReferenceConfig> assetReferences,
            List<DefaultSectionConfig> sections
    ) {}

    public record WidgetSettings(
            Size defaults,
            Size min,
            Size max,
            Integer maxItems
    ) {
        public int clampW(int requested) {
            return Math.max(min.w(), Math.min(max.w(), requested));
        }

        public int clampH(int requested) {
            return Math.max(min.h(), Math.min(max.h(), requested));
        }
    }

    public record Size(int w, int h) {}

    public record AssetReferenceConfig(MarketType type, String code) {}

    public record DefaultSectionConfig(String sectionId, WidgetKind kind, int order, MarketType marketType) {}

    public WidgetSettings settingsFor(WidgetKind kind) {
        WidgetSettings s = widgets.get(kind);
        if (s == null) {
            throw new IllegalStateException("Missing app.overview.widgets entry for kind=" + kind);
        }
        return s;
    }
}
