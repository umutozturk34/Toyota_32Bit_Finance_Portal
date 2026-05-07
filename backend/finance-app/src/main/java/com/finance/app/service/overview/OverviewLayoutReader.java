package com.finance.app.service.overview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        UserLayoutResponse response = userLayoutService.getOrEmpty(userSub);
        JsonNode overview = response != null ? response.overview() : null;
        ParseResult parsed = parse(overview);
        if (parsed.allIds().isEmpty()) return defaults.defaultSections();
        return mergeWithDefaults(parsed);
    }

    private ParseResult parse(JsonNode overview) {
        Set<String> allIds = new HashSet<>();
        Set<WidgetKind> allKinds = new HashSet<>();
        List<WidgetSection> visible = new ArrayList<>();
        if (overview == null) return new ParseResult(visible, allIds, allKinds);
        JsonNode sectionsNode = overview.get("sections");
        if (sectionsNode == null || !sectionsNode.isArray()) return new ParseResult(visible, allIds, allKinds);
        for (JsonNode entry : sectionsNode) {
            WidgetSection section = parseEntry(entry);
            if (section == null) continue;
            allIds.add(section.sectionId());
            allKinds.add(section.kind());
            if (entry.path("visible").asBoolean(true)) visible.add(section);
        }
        visible.sort(Comparator.comparingInt(WidgetSection::order));
        return new ParseResult(visible, allIds, allKinds);
    }

    private WidgetSection parseEntry(JsonNode entry) {
        String rawId = entry.path("sectionId").asText(null);
        if (rawId == null || rawId.isBlank()) return null;
        String sectionId = LEGACY_ID_MAP.getOrDefault(rawId, rawId);
        WidgetKind kind = parseKind(entry.path("kind").asText(null), sectionId);
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

    private WidgetKind inferLegacyKind(String sectionId) {
        return LEGACY_KIND_BY_PREFIX.entrySet().stream()
                .filter(e -> sectionId.equals(e.getKey()) || sectionId.startsWith(e.getKey() + "-"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private List<WidgetSection> mergeWithDefaults(ParseResult parsed) {
        List<WidgetSection> merged = new ArrayList<>(parsed.visible());
        int nextOrder = parsed.visible().stream().mapToInt(WidgetSection::order).max().orElse(-1) + 1;
        Set<WidgetKind> singletonKinds = Set.of(WidgetKind.ASSET_CARDS, WidgetKind.NEWS, WidgetKind.WATCHLIST);
        for (WidgetSection def : defaults.defaultSections()) {
            if (parsed.allIds().contains(def.sectionId())) continue;
            if (singletonKinds.contains(def.kind()) && parsed.allKinds().contains(def.kind())) continue;
            merged.add(new WidgetSection(def.sectionId(), def.kind(), nextOrder++, def.config()));
        }
        merged.sort(Comparator.comparingInt(WidgetSection::order));
        return merged;
    }

    private record ParseResult(List<WidgetSection> visible, Set<String> allIds, Set<WidgetKind> allKinds) {
    }
}
