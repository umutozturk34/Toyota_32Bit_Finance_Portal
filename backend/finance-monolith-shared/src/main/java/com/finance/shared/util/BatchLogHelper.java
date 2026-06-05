package com.finance.shared.util;

import org.apache.logging.log4j.Logger;

/**
 * Standardizes the summary log lines emitted after a {@link BatchUpdateRunner} run, keeping the
 * success/failure reporting format consistent across batch jobs.
 */
public final class BatchLogHelper {

    private BatchLogHelper() {
    }

    public static void logSummary(Logger log, String label, BatchUpdateRunner.Result result) {
        log.info("{}: {} success, {} failed", label, result.successCount(), result.failCount());
        if (!result.failedItems().isEmpty()) {
            log.warn("{} failed items: {}", label, result.failedItems());
        }
    }

    public static void logSummaryWithMetric(
            Logger log,
            String label,
            BatchUpdateRunner.Result result,
            String metricName,
            int metricValue) {
        log.info("{}: {}={}, {} success, {} failed",
                label, metricName, metricValue, result.successCount(), result.failCount());
        if (!result.failedItems().isEmpty()) {
            log.warn("{} failed items: {}", label, result.failedItems());
        }
    }
}
