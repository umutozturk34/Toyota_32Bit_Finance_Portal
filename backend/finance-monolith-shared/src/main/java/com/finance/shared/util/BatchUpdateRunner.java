package com.finance.shared.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

    /**
     * Concurrent counterpart of {@link #run}: processes items on a bounded pool of {@code parallelism}
     * threads, preserving the same per-item isolation, stop-condition short-circuit and failure-rate
     * guard. Use this when each item does an independent, latency-bound call (e.g. a per-symbol HTTP
     * fetch) so the wall-clock is the slowest few rather than the sum of all.
     *
     * <p>Semantics mirror the sequential run: once {@code stopCondition} matches, no further items are
     * started and {@code onStop} fires with the partial result; if {@link BatchFailureGuard} trips, its
     * exception propagates (aborting the run) exactly as in the sequential path. {@code processor} must be
     * thread-safe with respect to any shared state the caller mutates. A {@code parallelism <= 1} delegates
     * to {@link #run} so callers can disable concurrency by configuration.
     */
    public static <T> Result runParallel(
            Iterable<T> items,
            ThrowingConsumer<T> processor,
            Function<T, String> idExtractor,
            String failureType,
            int minSample,
            BiConsumer<T, Exception> onFailure,
            Predicate<Exception> stopCondition,
            BiConsumer<Result, Exception> onStop,
            int parallelism) {

        if (parallelism <= 1) {
            return run(items, processor, idExtractor, failureType, minSample, onFailure, stopCondition, onStop);
        }

        List<T> list = new ArrayList<>();
        items.forEach(list::add);

        AtomicInteger successCount = new AtomicInteger();
        List<String> failedItems = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean stopped = new AtomicBoolean(false);
        AtomicReference<Exception> stopException = new AtomicReference<>();
        AtomicReference<RuntimeException> guardException = new AtomicReference<>();
        Object failureLock = new Object();

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(parallelism, Math.max(1, list.size())));
        try {
            List<Future<?>> futures = new ArrayList<>(list.size());
            for (T item : list) {
                futures.add(pool.submit(() -> {
                    if (stopped.get()) return;
                    try {
                        processor.accept(item);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        if (stopCondition != null && stopCondition.test(e)) {
                            if (stopped.compareAndSet(false, true)) {
                                stopException.set(e);
                            }
                            return;
                        }
                        // Serialize failure bookkeeping + the failure-rate guard so counts stay consistent.
                        synchronized (failureLock) {
                            failedItems.add(idExtractor.apply(item));
                            if (onFailure != null) {
                                onFailure.accept(item, e);
                            }
                            try {
                                BatchFailureGuard.check(successCount.get(), failedItems.size(),
                                        failedItems, failureType, minSample);
                            } catch (RuntimeException guard) {
                                guardException.compareAndSet(null, guard);
                                stopped.set(true);
                            }
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception ignored) {
                    // Per-item exceptions are handled inside the task; a Future failure here is non-fatal.
                }
            }
        } finally {
            pool.shutdownNow();
        }

        Result result = new Result(successCount.get(), failedItems.size(), List.copyOf(failedItems));
        if (guardException.get() != null) {
            throw guardException.get();
        }
        if (stopped.get() && stopException.get() != null && onStop != null) {
            onStop.accept(result, stopException.get());
        }
        return result;
    }
}
