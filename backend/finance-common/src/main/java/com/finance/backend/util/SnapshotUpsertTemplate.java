package com.finance.backend.util;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SnapshotUpsertTemplate {

    private SnapshotUpsertTemplate() {
    }

    public static <E> E upsert(E existing, Consumer<E> updater, Supplier<E> creator) {
        if (existing != null) {
            updater.accept(existing);
            return existing;
        }
        return creator.get();
    }
}
