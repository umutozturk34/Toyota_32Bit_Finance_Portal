package com.finance.shared.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BatchUtilsTest {

    private final Logger log = LogManager.getLogger(BatchUtilsTest.class);

    @Test
    void enumDispatcher_mapsValues_byKeyFunction() {
        java.util.Map<EnumSample, String> map = EnumDispatcher.from(
                EnumSample.class, List.of("A", "B"),
                v -> EnumSample.valueOf(v));

        assertThat(map).hasSize(2);
        assertThat(map).containsKeys(EnumSample.A, EnumSample.B);
    }

    @Test
    void batchUpdateRunner_collectsSuccessesAndFailures() {
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                List.of("a", "b", "c"),
                item -> {
                    if (item.equals("b")) throw new RuntimeException("err");
                    ok.incrementAndGet();
                },
                item -> item, "test", 100,
                (item, e) -> fail.incrementAndGet(),
                null, null);

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failCount()).isEqualTo(1);
        assertThat(result.failedItems()).containsExactly("b");
        assertThat(fail).hasValue(1);
    }

    @Test
    void batchUpdateRunner_stopsEarly_whenStopConditionMet() {
        List<String> stopped = new ArrayList<>();
        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                List.of("a", "b", "c"),
                item -> { throw new RuntimeException("err"); },
                item -> item, "test", 100,
                null,
                e -> true,
                (r, e) -> stopped.add(e.getMessage()));

        assertThat(result.successCount()).isZero();
        assertThat(stopped).hasSize(1);
    }

    @Test
    void batchLogHelper_logSummary_doesNotThrow_evenWithFailures() {
        BatchUpdateRunner.Result result = new BatchUpdateRunner.Result(
                3, 2, List.of("a", "b"));

        BatchLogHelper.logSummary(log, "demo", result);
    }

    @Test
    void batchLogHelper_logSummaryWithMetric_doesNotThrow_withFailures() {
        BatchUpdateRunner.Result result = new BatchUpdateRunner.Result(2, 1, List.of("x"));

        BatchLogHelper.logSummaryWithMetric(log, "demo", result, "throughput", 12);
    }

    enum EnumSample { A, B }
}
