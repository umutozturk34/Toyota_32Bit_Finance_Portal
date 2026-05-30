package com.finance.notification.core.dispatch.slot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Maps slot names to the source keywords that resolve to them, letting handlers pick a content slot
 * (e.g. a market segment) from a free-text source string. Defaults to an empty mapping.
 */
@ConfigurationProperties(prefix = "notification")
public record SlotProperties(Map<String, List<String>> keywords) {

    public SlotProperties {
        keywords = keywords == null ? Map.of() : keywords;
    }
}
