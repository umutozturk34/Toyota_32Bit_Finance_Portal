package com.finance.common.util;

import org.apache.logging.log4j.Logger;

public final class BatchLogHelper {

    private BatchLogHelper() {
    }

    public static void logSummary(Logger log, String label, BatchUpdateRunner.Result result) {
        log.info("{}: {} success, {} failed", label, result.successCount(), result.failCount());
        if (!result.failedItems().isEmpty()) {
            log.warn("{} failed items: {}", label, result.failedItems());
        }
    }

    public static void logSummaryWithTotal(Logger log, String label, BatchUpdateRunner.Result result, int total) {
        log.info("{}: {} success, {} failed out of {} total",
                label, result.successCount(), result.failCount(), total);
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
