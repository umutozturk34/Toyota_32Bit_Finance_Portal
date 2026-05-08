package com.finance.app.service.overview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Log4j2
@Component
@RequiredArgsConstructor
public class OverviewLayoutReader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
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
        Map<String, Object> raw = response != null ? response.overview() : null;
        JsonNode overview = raw != null && !raw.isEmpty() ? OBJECT_MAPPER.valueToTree(raw) : null;
        ParseResult parsed = parse(overview);
        if (parsed.allIds().isEmpty()) return defaults.defaultSections();
        return parsed.visible();
    }

    private ParseResult parse(JsonNode overview) {
        Set<String> allIds = new HashSet<>();
        Set<WidgetKind> allKinds = new HashSet<>();
        LinkedHashMap<String, WidgetSection> dedup = new LinkedHashMap<>();
        int assetCardCount = 0;
        int maxAssetCards = defaults.maxAssetCardWidgetsPerLayout();
        if (overview == null) return new ParseResult(List.of(), allIds, allKinds);
        JsonNode sectionsNode = overview.get("sections");
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
            return "MOVERS:" + (market != null && !market.isNull() ? market.asText() : "any");
        }
        return s.kind().name();
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
