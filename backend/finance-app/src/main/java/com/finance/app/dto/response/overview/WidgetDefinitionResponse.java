package com.finance.app.dto.response.overview;

import java.util.List;

/** Catalog of widget definitions (size/item bounds per kind) and global layout limits for the client UI. */
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
