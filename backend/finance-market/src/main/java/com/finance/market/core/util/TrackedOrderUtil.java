package com.finance.market.core.util;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class TrackedOrderUtil {

    private TrackedOrderUtil() {
    }

    public static <T> List<T> sortByTrackedCodes(List<T> items,
                                                 List<String> orderedCodes,
                                                 Function<T, String> codeExtractor) {
        if (items == null || items.isEmpty() || orderedCodes == null || orderedCodes.isEmpty()) {
            return items;
        }
        Map<String, Integer> orderIndex = IntStream.range(0, orderedCodes.size()).boxed()
                .collect(Collectors.toUnmodifiableMap(orderedCodes::get, Function.identity()));
        return items.stream()
                .sorted(Comparator.comparingInt(item -> orderIndex.getOrDefault(codeExtractor.apply(item), Integer.MAX_VALUE)))
                .toList();
    }
}