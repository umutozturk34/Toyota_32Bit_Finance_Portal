package com.finance.app.dto.response.overview;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

public record WidgetSection(
        String sectionId,
        WidgetKind kind,
        int order,
        JsonNode config
) {

    public WidgetSection {
        if (config == null) config = JsonNodeFactory.instance.objectNode();
    }
}
