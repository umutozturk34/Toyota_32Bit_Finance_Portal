package com.finance.app.dto.response.overview;

import java.util.List;

/** Catalog of widget definitions (size/item bounds per kind) and global layout limits for the client UI. */
public record WidgetDefinitionResponse(
        List<WidgetDefinition> widgets,
        Limits limits
) {

    /**
     * Describes a single widget kind: its default grid size plus the allowed size range and,
     * where applicable, the maximum number of items it may display.
     *
     * @param maxItems item cap for list-style widgets, or {@code null} when no item limit applies
     */
    public record WidgetDefinition(
            WidgetKind kind,
            Size defaults,
            Size min,
            Size max,
            Integer maxItems
    ) {}

    /** A widget footprint on the dashboard grid, in columns ({@code w}) and rows ({@code h}). */
    public record Size(int w, int h) {}

    /**
     * Global dashboard-layout constraints enforced on the client: overall widget count, the
     * stricter cap on asset-card widgets, the per-widget configuration item limit and the maximum
     * number of grid rows.
     */
    public record Limits(
            int maxWidgetsPerLayout,
            int maxAssetCardWidgetsPerLayout,
            int maxConfigLimit,
            int maxLayoutRows
    ) {}
}
