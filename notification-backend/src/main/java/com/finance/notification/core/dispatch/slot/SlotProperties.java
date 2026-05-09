package com.finance.notification.core.dispatch.slot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "notification.slot")
public record SlotProperties(Map<String, List<String>> keywords) {

    public SlotProperties {
        keywords = keywords == null ? Map.of() : keywords;
    }
}
