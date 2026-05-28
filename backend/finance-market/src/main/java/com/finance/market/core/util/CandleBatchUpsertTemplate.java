package com.finance.market.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generic batch upsert by date/key: loads existing entities for the incoming keys in one query,
 * mutates matches in place via the {@code updater}, and collects the rest as new entities to insert.
 * Returns the new entities and insert/update counts; the caller persists the new entities.
 */
public final class CandleBatchUpsertTemplate {

    private CandleBatchUpsertTemplate() {
    }

    /** Outcome of an upsert: entities to insert plus insert/update counts. */
    public record UpsertResult<E>(List<E> newEntities, int insertCount, int updateCount) {
        public int totalChanged() {
            return insertCount + updateCount;
        }
    }

    /**
     * Upserts {@code dtos} against existing entities keyed by {@code K}: existing keys are updated,
     * unseen keys are created. Existing entities are mutated in place (managed by the caller's tx).
     */
    public static <D, E, K>UpsertResult<E> upsert(
            List<D> dtos,
            Function<D, K> dtoKeyExtractor,
            Function<List<K>, List<E>> existingLoader,
            Function<E, K> entityKeyExtractor,
            BiConsumer<E, D> updater,
            Function<D, E> creator) {

        List<K> keys = dtos.stream().map(dtoKeyExtractor::apply).toList();
        Map<K, E> existingMap = existingLoader.apply(keys).stream()
                .collect(Collectors.toMap(entityKeyExtractor, Function.identity()));

        List<E> toInsert = new ArrayList<>();
        int updateCount = 0;

        for (D dto : dtos) {
            K key = dtoKeyExtractor.apply(dto);
            E existing = existingMap.get(key);
            if (existing != null) {
                updater.accept(existing, dto);
                updateCount++;
            } else {
                toInsert.add(creator.apply(dto));
            }
        }

        return new UpsertResult<>(toInsert, toInsert.size(), updateCount);
    }
}
