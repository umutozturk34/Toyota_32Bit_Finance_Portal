package com.finance.notification.core.dispatch.slot;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a source string to its configured slot by case-insensitive keyword containment.
 * An unmatched source yields empty (logged), letting handlers fall back to a generic title.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class SlotResolver {

    private final SlotProperties properties;

    public Optional<String> slotFor(String source) {
        if (source == null || source.isBlank()) return Optional.empty();
        String lower = source.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : properties.keywords().entrySet()) {
            for (String keyword : entry.getValue()) {
                if (keyword != null && !keyword.isBlank() && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        log.warn("Unknown slot source token={} — handlers will fall back to generic title", source);
        return Optional.empty();
    }
}
