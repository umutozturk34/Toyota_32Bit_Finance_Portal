package com.finance.backend.util;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class TrackedOrderUtil {

    private TrackedOrderUtil() {
    }

    public static <T> List<T> sortByTrackedCodes(List<T> items,
                                                 List<String> orderedCodes,
                                                 Function<T, String> codeExtractor) {
        if (items == null || items.isEmpty() || orderedCodes == null || orderedCodes.isEmpty()) {
            return items;
        }

        Map<String, Integer> orderIndex = new HashMap<>(orderedCodes.size());
        for (int i = 0; i < orderedCodes.size(); i++) {
            orderIndex.put(orderedCodes.get(i), i);
        }

        return items.stream()
                .sorted(Comparator.comparingInt(item -> orderIndex.getOrDefault(codeExtractor.apply(item), Integer.MAX_VALUE)))
                .toList();
    }
}