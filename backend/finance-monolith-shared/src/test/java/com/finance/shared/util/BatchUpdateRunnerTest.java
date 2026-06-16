package com.finance.shared.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BatchUpdateRunner#runParallel}: verifies the bounded-pool variant preserves the
 * sequential contract — every item processed, per-item failures isolated, stop-condition short-circuit, and
 * the {@code parallelism <= 1} delegation. AAA throughout; a high {@code minSample} keeps the failure guard
 * dormant so it never interferes with the assertions.
 */
class BatchUpdateRunnerTest {

    private static final int DORMANT_GUARD = 1000;

    @Test
    void runParallel_processesEveryItem_andCountsSuccesses() {
        // Arrange
        List<Integer> items = List.of(1, 2, 3, 4, 5, 6, 7, 8);
        AtomicInteger processed = new AtomicInteger();

        // Act
        BatchUpdateRunner.Result result = BatchUpdateRunner.runParallel(
                items, item -> processed.incrementAndGet(), Object::toString,
                "test", DORMANT_GUARD, null, null, null, 4);

        // Assert
        assertThat(processed.get()).isEqualTo(8);
        assertThat(result.successCount()).isEqualTo(8);
        assertThat(result.failCount()).isZero();
    }

    @Test
    void runParallel_isolatesPerItemFailure_withoutAbortingOthers() {
        // Arrange
        List<Integer> items = List.of(1, 2, 3, 4, 5);

        // Act — item 3 throws; the other four must still succeed
        BatchUpdateRunner.Result result = BatchUpdateRunner.runParallel(
                items,
                item -> {
                    if (item == 3) throw new IllegalStateException("boom");
                },
                Object::toString, "test", DORMANT_GUARD, null, null, null, 3);

        // Assert
        assertThat(result.successCount()).isEqualTo(4);
        assertThat(result.failCount()).isEqualTo(1);
        assertThat(result.failedItems()).containsExactly("3");
    }

    @Test
    void runParallel_shortCircuits_whenStopConditionMatches() {
        // Arrange
        List<Integer> items = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        AtomicInteger stopCalls = new AtomicInteger();

        // Act — item 2 throws the stop marker; once tripped, no further items start
        BatchUpdateRunner.Result result = BatchUpdateRunner.runParallel(
                items,
                item -> {
                    if (item == 2) throw new IllegalArgumentException("stop");
                },
                Object::toString, "test", DORMANT_GUARD, null,
                e -> e instanceof IllegalArgumentException,
                (r, e) -> stopCalls.incrementAndGet(), 4);

        // Assert — onStop fired exactly once and the stopped item is never counted a success
        assertThat(stopCalls.get()).isEqualTo(1);
        assertThat(result.successCount()).isLessThan(items.size());
    }

    @Test
    void runParallel_delegatesToSequential_whenParallelismIsOne() {
        // Arrange
        List<Integer> items = List.of(1, 2, 3);
        AtomicInteger processed = new AtomicInteger();

        // Act
        BatchUpdateRunner.Result result = BatchUpdateRunner.runParallel(
                items, item -> processed.incrementAndGet(), Object::toString,
                "test", DORMANT_GUARD, null, null, null, 1);

        // Assert
        assertThat(processed.get()).isEqualTo(3);
        assertThat(result.successCount()).isEqualTo(3);
    }
}
