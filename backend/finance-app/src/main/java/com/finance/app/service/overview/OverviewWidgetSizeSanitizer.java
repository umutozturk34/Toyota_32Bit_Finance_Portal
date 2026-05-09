package com.finance.app.service.overview;

import com.finance.app.config.OverviewProperties;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.user.service.OverviewSaveSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(20)
@RequiredArgsConstructor
public class OverviewWidgetSizeSanitizer implements OverviewSaveSanitizer {

    private final OverviewProperties properties;

    @Override
    public Map<String, Object> sanitize(Map<String, Object> overview) {
        if (overview == null) return Map.of();
        Object sectionsObj = overview.get("sections");
        if (!(sectionsObj instanceof List<?> sections)) return overview;
        List<Object> clamped = new ArrayList<>(sections.size());
        for (Object o : sections) {
            if (!(o instanceof Map<?, ?> entry)) {
                clamped.add(o);
                continue;
            }
            clamped.add(clampEntry(entry));
        }
        Map<String, Object> result = new LinkedHashMap<>(overview);
        result.put("sections", clamped);
        return result;
    }

    private Map<String, Object> clampEntry(Map<?, ?> entry) {
        LinkedHashMap<String, Object> mutable = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : entry.entrySet()) {
            mutable.put(String.valueOf(e.getKey()), e.getValue());
        }
        WidgetKind kind = resolveKind(mutable.get("kind"));
        if (kind == null) return mutable;
        OverviewProperties.WidgetSettings settings = properties.settingsFor(kind);
        Object w = mutable.get("w");
        Object h = mutable.get("h");
        int clampedW = w instanceof Number wn ? settings.clampW(wn.intValue()) : settings.defaults().w();
        int clampedH = h instanceof Number hn ? settings.clampH(hn.intValue()) : settings.defaults().h();
        mutable.put("w", clampedW);
        mutable.put("h", clampedH);
        Object y = mutable.get("y");
        int maxY = Math.max(0, properties.limits().maxLayoutRows() - clampedH);
        if (y instanceof Number yn) mutable.put("y", Math.max(0, Math.min(maxY, yn.intValue())));
        return mutable;
    }

    private WidgetKind resolveKind(Object raw) {
        if (!(raw instanceof String s) || s.isBlank()) return null;
        try {
            return WidgetKind.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
