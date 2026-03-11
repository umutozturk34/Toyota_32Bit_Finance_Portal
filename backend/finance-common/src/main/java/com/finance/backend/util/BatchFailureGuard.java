package com.finance.backend.util;

import com.finance.backend.exception.BusinessException;
import lombok.extern.log4j.Log4j2;

import java.util.List;

@Log4j2
public final class BatchFailureGuard {

    private static final double FAILURE_THRESHOLD = 0.5;
    private static final int DEFAULT_MIN_SAMPLE = 5;

    private BatchFailureGuard() {}

    public static void check(int successCount, int failCount, List<String> failedItems, String type) {
        check(successCount, failCount, failedItems, type, DEFAULT_MIN_SAMPLE);
    }

    public static void check(int successCount, int failCount,
                             List<String> failedItems, String type, int minSample) {
        int total = successCount + failCount;
        double failureRate = (double) failCount / total;
        if (failureRate > FAILURE_THRESHOLD && total >= minSample) {
            log.error("CRITICAL API ERROR: Failure rate {}% exceeded threshold during {} update. Failed: {}",
                    String.format("%.1f", failureRate * 100), type, failedItems);
            throw new BusinessException(
                    String.format("Critical API failure: %d out of %d %s failed (%.1f%%)",
                            failCount, total, type, failureRate * 100),
                    "CRITICAL_API_FAILURE");
        }
    }
}
