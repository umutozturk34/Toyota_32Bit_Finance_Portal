package com.finance.app.service.overview;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.finance.app.config.OverviewProperties;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.common.exception.BusinessException;
import com.finance.user.service.OverviewSaveSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Save-time sanitizer (runs after dedup) that validates each section's width/height and vertical placement
 * against the per-kind size bounds and max layout rows, writing back resolved defaults. Out-of-bounds sizes
 * or rows are rejected.
 */
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
        WidgetKind kind = resolveKind(entry.path("kind").asString(null));
        if (kind == null) return entry;
        OverviewProperties.WidgetSettings settings = properties.settingsFor(kind);
        int reqW = entry.path("w").isNumber() ? entry.get("w").asInt() : settings.defaults().w();
        int reqH = entry.path("h").isNumber() ? entry.get("h").asInt() : settings.defaults().h();
        if (reqW < settings.min().w() || reqW > settings.max().w()) {
            throw new BusinessException("error.overview.widgetSizeExceeded", "width", settings.max().w());
        }
        if (reqH < settings.min().h() || reqH > settings.max().h()) {
            throw new BusinessException("error.overview.widgetSizeExceeded", "height", settings.max().h());
        }
        entry.put("w", reqW);
        entry.put("h", reqH);
        int maxLayoutRows = properties.limits().maxLayoutRows();
        if (entry.path("y").isNumber()) {
            int y = entry.get("y").asInt();
            if (y < 0 || y + reqH > maxLayoutRows) {
                throw new BusinessException("error.overview.layoutRowsExceeded", maxLayoutRows);
            }
            entry.put("y", y);
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
