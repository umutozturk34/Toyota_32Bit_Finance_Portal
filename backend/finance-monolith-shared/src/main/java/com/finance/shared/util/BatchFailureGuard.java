package com.finance.shared.util;

import com.finance.common.exception.CriticalApiFailureException;
import lombok.extern.log4j.Log4j2;

import java.util.List;

/**
 * Circuit-breaker guard for batch external-API updates: aborts a batch by throwing when the failure
 * rate exceeds 50% on a sufficiently large sample, so transient noise is tolerated but a systemic
 * outage fails fast instead of corrupting data with stale values.
 */
@Log4j2
public final class BatchFailureGuard {

    private static final double FAILURE_THRESHOLD = 0.5;
    private static final int DEFAULT_MIN_SAMPLE = 5;

    private BatchFailureGuard() {}

    public static void check(int successCount, int failCount, List<String> failedItems, String type) {
        check(successCount, failCount, failedItems, type, DEFAULT_MIN_SAMPLE);
    }

    /**
     * Aborts the batch when the failure rate exceeds the threshold and the sample reaches
     * {@code minSample}.
     *
     * @throws CriticalApiFailureException tagged {@code CRITICAL_API_FAILURE} when tripped
     */
    public static void check(int successCount, int failCount,
                             List<String> failedItems, String type, int minSample) {
        int total = successCount + failCount;
        double failureRate = (double) failCount / total;
        if (failureRate > FAILURE_THRESHOLD && total >= minSample) {
            log.error("CRITICAL API ERROR: Failure rate {}% exceeded threshold during {} update. Failed: {}",
                    String.format("%.1f", failureRate * 100), type, failedItems);
            throw new CriticalApiFailureException(
                    String.format("Critical API failure: %d out of %d %s failed (%.1f%%)",
                            failCount, total, type, failureRate * 100));
        }
    }
}
