package com.finance.app.dto.response.overview;

/** A rendered overview widget: its section id, kind, order, and payload (null {@code data} = empty widget). */
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
