package com.finance.shared.util;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Builds an {@link EnumMap} index from a collection of values keyed by an enum, giving O(1)
 * enum-keyed dispatch; later values silently overwrite earlier ones sharing a key.
 */
public final class EnumDispatcher {

    private EnumDispatcher() {
    }

    public static <K extends Enum<K>, V> Map<K, V> from(Class<K> enumClass,
                                                         Collection<V> values,
                                                         Function<V, K> keyFn) {
        Map<K, V> map = new EnumMap<>(enumClass);
        values.forEach(v -> map.put(keyFn.apply(v), v));
        return map;
    }
}
