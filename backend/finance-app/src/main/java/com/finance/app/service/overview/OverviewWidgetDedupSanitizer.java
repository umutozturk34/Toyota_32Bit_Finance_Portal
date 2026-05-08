package com.finance.app.service.overview;

import com.finance.user.service.OverviewSaveSanitizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OverviewWidgetDedupSanitizer implements OverviewSaveSanitizer {

    public static final int MAX_WIDGETS = 12;
    public static final int MAX_ASSET_CARDS = 4;

    @Override
    public Map<String, Object> sanitize(Map<String, Object> overview) {
        if (overview == null) return Map.of();
        Object sectionsObj = overview.get("sections");
        if (!(sectionsObj instanceof List<?> sections)) return overview;
        LinkedHashMap<String, Object> dedup = new LinkedHashMap<>();
        int assetCardCount = 0;
        for (Object o : sections) {
            if (!(o instanceof Map<?, ?> entry)) continue;
            Object visibleFlag = entry.get("visible");
            if (visibleFlag instanceof Boolean v && !v) continue;
            Object kind = entry.get("kind");
            if ("ASSET_CARDS".equals(kind)) {
                if (assetCardCount >= MAX_ASSET_CARDS) continue;
                assetCardCount++;
            }
            String key = dedupKey(entry);
            dedup.putIfAbsent(key, entry);
            if (dedup.size() >= MAX_WIDGETS) break;
        }
        Map<String, Object> result = new LinkedHashMap<>(overview);
        result.put("sections", new ArrayList<>(dedup.values()));
        return result;
    }

    private String dedupKey(Map<?, ?> entry) {
        Object kind = entry.get("kind");
        Object configObj = entry.get("config");
        Map<?, ?> config = configObj instanceof Map<?, ?> map ? map : Map.of();
        if ("ASSET_CARDS".equals(kind)) {
            Object sectionId = entry.get("sectionId");
            return "ASSET_CARDS:" + (sectionId == null ? java.util.UUID.randomUUID() : sectionId);
        }
        if ("WATCHLIST".equals(kind)) {
            Object wlId = config.get("watchlistId");
            return "WATCHLIST:" + (wlId == null ? "default" : wlId);
        }
        if ("MOVERS".equals(kind)) {
            Object market = config.get("market");
            return "MOVERS:" + (market == null ? "any" : market);
        }
        return String.valueOf(kind);
    }
}
