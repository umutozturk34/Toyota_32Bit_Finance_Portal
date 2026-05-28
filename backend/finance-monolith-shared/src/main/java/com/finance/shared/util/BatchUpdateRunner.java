package com.finance.shared.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Generic per-item batch executor: iterates items, isolating each item's failure so one bad item
 * does not abort the run, while still letting callers short-circuit on fatal errors and enforcing
 * the {@link BatchFailureGuard} threshold as failures accumulate.
 */
public final class BatchUpdateRunner {

    private BatchUpdateRunner() {
    }

    /** Item processor permitted to throw checked exceptions, captured by the runner per item. */
    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T item) throws Exception;
    }

    /** Outcome of a batch run: success/failure counts and the ids of items that failed. */
    public record Result(int successCount, int failCount, List<String> failedItems) {
    }

    /**
     * Processes each item, recording per-item failures (via {@code idExtractor}/{@code onFailure})
     * and applying the failure-rate guard. If {@code stopCondition} matches an exception the loop
     * breaks early, invoking {@code onStop} with the partial result; the partial result is returned.
     */
    public static <T> Result run(
            Iterable<T> items,
            ThrowingConsumer<T> processor,
            Function<T, String> idExtractor,
            String failureType,
            int minSample,
            BiConsumer<T, Exception> onFailure,
            Predicate<Exception> stopCondition,
            BiConsumer<Result, Exception> onStop) {

        int successCount = 0;
        int failCount = 0;
        List<String> failedItems = new ArrayList<>();

        for (T item : items) {
            try {
                processor.accept(item);
                successCount++;
            } catch (Exception e) {
                if (stopCondition != null && stopCondition.test(e)) {
                    if (onStop != null) {
                        onStop.accept(new Result(successCount, failCount, List.copyOf(failedItems)), e);
                    }
                    break;
                }

                failCount++;
                failedItems.add(idExtractor.apply(item));

                if (onFailure != null) {
                    onFailure.accept(item, e);
                }

                BatchFailureGuard.check(successCount, failCount, failedItems, failureType, minSample);
            }
        }

        return new Result(successCount, failCount, List.copyOf(failedItems));
    }
}
