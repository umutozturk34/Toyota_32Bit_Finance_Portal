package com.finance.app.dto.response.overview;

public record RenderedWidget(
        String sectionId,
        WidgetKind kind,
        int order,
        WidgetData data
) {

    public static RenderedWidget of(String sectionId, int order, WidgetData data) {
        return new RenderedWidget(sectionId, data.kind(), order, data);
    }

    public static RenderedWidget empty(String sectionId, WidgetKind kind, int order) {
        return new RenderedWidget(sectionId, kind, order, null);
    }
}
