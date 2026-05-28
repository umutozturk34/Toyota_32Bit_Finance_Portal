package com.finance.app.service.overview;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.finance.app.config.OverviewProperties;
import com.finance.common.exception.BusinessException;
import com.finance.user.service.OverviewSaveSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Save-time sanitizer (runs first) that enforces the per-layout widget and asset-card counts, then drops
 * duplicate visible sections keyed by kind (with watchlist/movers keyed by their config target). Rejects
 * layouts that exceed the configured maximums.
 */
@Component
@Order(10)
@RequiredArgsConstructor
public class OverviewWidgetDedupSanitizer implements OverviewSaveSanitizer {

    private final OverviewProperties properties;

    @Override
    public JsonNode sanitize(JsonNode overview) {
        if (overview == null || !overview.isObject()) return overview;
        JsonNode sectionsNode = overview.get("sections");
        if (sectionsNode == null || !sectionsNode.isArray()) return overview;
        int maxWidgets = properties.limits().maxWidgetsPerLayout();
        int maxAssetCards = properties.limits().maxAssetCardWidgetsPerLayout();

        int visibleCount = 0;
        int assetCardCount = 0;
        for (JsonNode entry : sectionsNode) {
            if (!entry.isObject()) continue;
            JsonNode visibleFlag = entry.get("visible");
            if (visibleFlag != null && visibleFlag.isBoolean() && !visibleFlag.asBoolean()) continue;
            visibleCount++;
            if ("ASSET_CARDS".equals(entry.path("kind").asString(null))) assetCardCount++;
        }
        if (visibleCount > maxWidgets) {
            throw new BusinessException("error.overview.maxWidgetsExceeded", maxWidgets);
        }
        if (assetCardCount > maxAssetCards) {
            throw new BusinessException("error.overview.maxAssetCardsExceeded", maxAssetCards);
        }

        LinkedHashMap<String, JsonNode> dedup = new LinkedHashMap<>();
        for (JsonNode entry : sectionsNode) {
            if (!entry.isObject()) continue;
            JsonNode visibleFlag = entry.get("visible");
            if (visibleFlag != null && visibleFlag.isBoolean() && !visibleFlag.asBoolean()) continue;
            String kind = entry.path("kind").asString(null);
            dedup.putIfAbsent(dedupKey(entry, kind), entry);
        }
        ArrayNode deduped = JsonNodeFactory.instance.arrayNode(dedup.size());
        dedup.values().forEach(deduped::add);
        ((ObjectNode) overview).set("sections", deduped);
        return overview;
    }

    private String dedupKey(JsonNode entry, String kind) {
        JsonNode config = entry.path("config");
        if ("ASSET_CARDS".equals(kind)) {
            JsonNode sectionId = entry.get("sectionId");
            return "ASSET_CARDS:" + (sectionId == null || sectionId.isNull() ? UUID.randomUUID() : sectionId.asString());
        }
        if ("WATCHLIST".equals(kind)) {
            JsonNode wlId = config.get("watchlistId");
            return "WATCHLIST:" + (wlId == null || wlId.isNull() ? "default" : wlId.asString());
        }
        if ("MOVERS".equals(kind)) {
            JsonNode market = config.get("market");
            return "MOVERS:" + (market == null || market.isNull() ? "any" : market.asString());
        }
        return kind == null ? "null" : kind;
    }
}
