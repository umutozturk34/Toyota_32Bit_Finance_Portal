package com.finance.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class BatchUpdateRunner {

    private BatchUpdateRunner() {
    }

    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T item) throws Exception;
    }

    public record Result(int successCount, int failCount, List<String> failedItems) {
    }

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
