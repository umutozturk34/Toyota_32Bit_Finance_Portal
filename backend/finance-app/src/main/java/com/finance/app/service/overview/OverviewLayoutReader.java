package com.finance.app.service.overview;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import com.finance.user.dto.UserLayoutResponse;
import com.finance.user.service.UserLayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a user's stored overview layout JSON and resolves the ordered, visible widget sections to render,
 * tolerating both the legacy single-page ({@code sections}) and multi-page ({@code pages}) shapes. Maps
 * legacy section ids/kinds, dedupes per kind, caps asset-card widgets, and falls back to defaults only when
 * the user has no layout at all.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class OverviewLayoutReader {

    private static final Map<String, String> LEGACY_ID_MAP = Map.of("bist-indices", "asset-cards-default");
    private static final Map<String, WidgetKind> LEGACY_KIND_BY_PREFIX = Map.of(
            "asset-cards", WidgetKind.ASSET_CARDS,
            "bist-indices", WidgetKind.ASSET_CARDS,
            "movers", WidgetKind.MOVERS,
            "watchlist", WidgetKind.WATCHLIST,
            "news", WidgetKind.NEWS);

    private final UserLayoutService userLayoutService;
    private final OverviewDefaults defaults;

    public List<WidgetSection> readVisibleSections(String userSub) {
        return readVisibleSections(userSub, null);
    }

    public List<WidgetSection> readVisibleSections(String userSub, String pageId) {
        UserLayoutResponse response = userLayoutService.getOrEmpty(userSub);
        JsonNode overview = response != null ? response.overview() : null;
        if (overview == null || overview.isNull() || (overview.isObject() && overview.isEmpty())) overview = null;
        boolean overviewMissing = overview == null
                || (!overview.has("pages") && !overview.has("sections"));
        JsonNode sectionsArray = resolveSectionsArray(overview, pageId);
        if (sectionsArray == null) {
            return overviewMissing ? defaults.defaultSections() : List.of();
        }
        ParseResult parsed = parse(sectionsArray);
        if (parsed.allIds().isEmpty() && overviewMissing) return defaults.defaultSections();
        return parsed.visible();
    }

    /**
     * Locates the sections array for the requested page: for multi-page layouts returns the named page (or
     * the first when {@code pageId} is blank, null if not found); for legacy layouts the top-level sections.
     */
    private JsonNode resolveSectionsArray(JsonNode overview, String pageId) {
        if (overview == null) return null;
        JsonNode pages = overview.get("pages");
        if (pages != null && pages.isArray() && !pages.isEmpty()) {
            if (pageId == null || pageId.isBlank()) {
                JsonNode first = pages.get(0);
                return first != null ? first.get("sections") : null;
            }
            for (JsonNode page : pages) {
                String id = page.path("id").asString(null);
                if (pageId.equals(id)) {
                    return page.get("sections");
                }
            }
            return null;
        }
        return overview.get("sections");
    }

    private ParseResult parse(JsonNode sectionsNode) {
        Set<String> allIds = new HashSet<>();
        Set<WidgetKind> allKinds = new HashSet<>();
        LinkedHashMap<String, WidgetSection> dedup = new LinkedHashMap<>();
        int assetCardCount = 0;
        int maxAssetCards = defaults.maxAssetCardWidgetsPerLayout();
        if (sectionsNode == null || !sectionsNode.isArray()) return new ParseResult(List.of(), allIds, allKinds);
        for (JsonNode entry : sectionsNode) {
            WidgetSection section = parseEntry(entry);
            if (section == null) continue;
            allIds.add(section.sectionId());
            allKinds.add(section.kind());
            if (!entry.path("visible").asBoolean(true)) continue;
            if (section.kind() == WidgetKind.ASSET_CARDS) {
                if (assetCardCount >= maxAssetCards) continue;
                assetCardCount++;
            }
            dedup.putIfAbsent(dedupKey(section), section);
        }
        List<WidgetSection> visible = new ArrayList<>(dedup.values());
        visible.sort(Comparator.comparingInt(WidgetSection::order));
        return new ParseResult(visible, allIds, allKinds);
    }

    private static String dedupKey(WidgetSection s) {
        if (s.kind() == WidgetKind.ASSET_CARDS) {
            return "ASSET_CARDS:" + s.sectionId();
        }
        if (s.kind() == WidgetKind.WATCHLIST) {
            JsonNode wlId = s.config().get("watchlistId");
            return "WATCHLIST:" + (wlId != null && wlId.isNumber() ? wlId.asLong() : "default");
        }
        if (s.kind() == WidgetKind.MOVERS) {
            JsonNode market = s.config().get("market");
            return "MOVERS:" + (market != null && !market.isNull() ? market.asString() : "any");
        }
        return s.kind().name();
    }

    private WidgetSection parseEntry(JsonNode entry) {
        String rawId = entry.path("sectionId").asString(null);
        if (rawId == null || rawId.isBlank()) return null;
        String sectionId = LEGACY_ID_MAP.getOrDefault(rawId, rawId);
        WidgetKind kind = parseKind(entry.path("kind").asString(null), sectionId);
        if (kind == null) return null;
        int order = entry.path("order").asInt(0);
        JsonNode config = entry.has("config") && entry.get("config").isObject()
                ? entry.get("config")
                : JsonNodeFactory.instance.objectNode();
        return new WidgetSection(sectionId, kind, order, config);
    }

    private WidgetKind parseKind(String raw, String sectionId) {
        if (raw == null || raw.isBlank()) return inferLegacyKind(sectionId);
        try {
            return WidgetKind.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            log.debug("OverviewLayoutReader unknown kind={} for section={}", raw, sectionId);
            return null;
        }
    }

    /** Infers a kind from a legacy section id by matching a known prefix when no explicit kind is present. */
    private WidgetKind inferLegacyKind(String sectionId) {
        return LEGACY_KIND_BY_PREFIX.entrySet().stream()
                .filter(e -> sectionId.equals(e.getKey()) || sectionId.startsWith(e.getKey() + "-"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private record ParseResult(List<WidgetSection> visible, Set<String> allIds, Set<WidgetKind> allKinds) {
    }
}
