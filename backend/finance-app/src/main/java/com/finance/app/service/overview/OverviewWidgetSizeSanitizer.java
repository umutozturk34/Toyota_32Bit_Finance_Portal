package com.finance.app.service.overview;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.finance.app.config.OverviewProperties;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.user.service.OverviewSaveSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
@RequiredArgsConstructor
public class OverviewWidgetSizeSanitizer implements OverviewSaveSanitizer {

    private final OverviewProperties properties;

    @Override
    public JsonNode sanitize(JsonNode overview) {
        if (overview == null || !overview.isObject()) return overview;
        JsonNode sectionsNode = overview.get("sections");
        if (sectionsNode == null || !sectionsNode.isArray()) return overview;
        ArrayNode clamped = JsonNodeFactory.instance.arrayNode(sectionsNode.size());
        for (JsonNode entry : sectionsNode) {
            if (!entry.isObject()) {
                clamped.add(entry);
                continue;
            }
            clamped.add(clampEntry((ObjectNode) entry.deepCopy()));
        }
        ((ObjectNode) overview).set("sections", clamped);
        return overview;
    }

    private JsonNode clampEntry(ObjectNode entry) {
        WidgetKind kind = resolveKind(entry.path("kind").asText(null));
        if (kind == null) return entry;
        OverviewProperties.WidgetSettings settings = properties.settingsFor(kind);
        int clampedW = entry.path("w").isNumber()
                ? settings.clampW(entry.get("w").asInt())
                : settings.defaults().w();
        int clampedH = entry.path("h").isNumber()
                ? settings.clampH(entry.get("h").asInt())
                : settings.defaults().h();
        entry.put("w", clampedW);
        entry.put("h", clampedH);
        int maxY = Math.max(0, properties.limits().maxLayoutRows() - clampedH);
        if (entry.path("y").isNumber()) {
            entry.put("y", Math.max(0, Math.min(maxY, entry.get("y").asInt())));
        }
        return entry;
    }

    private WidgetKind resolveKind(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return WidgetKind.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
