package com.finance.app.dto.response.overview;

import java.util.List;

public record WidgetDefinitionResponse(
        List<WidgetDefinition> widgets,
        Limits limits
) {

    public record WidgetDefinition(
            WidgetKind kind,
            Size defaults,
            Size min,
            Size max,
            Integer maxItems
    ) {}

    public record Size(int w, int h) {}

    public record Limits(
            int maxWidgetsPerLayout,
            int maxAssetCardWidgetsPerLayout,
            int maxConfigLimit,
            int maxLayoutRows
    ) {}
}
