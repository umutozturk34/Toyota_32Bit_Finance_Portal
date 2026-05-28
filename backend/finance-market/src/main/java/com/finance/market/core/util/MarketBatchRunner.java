package com.finance.market.core.util;

import com.finance.shared.util.BatchUpdateRunner;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;

/**
 * Runs a per-item market batch operation with shared error handling: logs each item failure and
 * aborts early (logging partial results) when the resilience circuit breaker is open.
 */
public final class MarketBatchRunner {

    private MarketBatchRunner() {
    }

    /** Processes each item, isolating per-item failures and stopping early on an open circuit breaker. */
    public static <T> BatchUpdateRunner.Result run(
            Iterable<T> items,
            BatchUpdateRunner.ThrowingConsumer<T> processor,
            Function<T, String> idExtractor,
            Logger log,
            String marketName,
            String operation,
            int minSample) {

        return BatchUpdateRunner.run(
                items,
                processor,
                idExtractor,
                operation,
                minSample,
                (item, e) -> log.error("Failed to {} for {}: {}",
                        operation, idExtractor.apply(item), e.getMessage(), e),
                e -> e instanceof CallNotPermittedException,
                (stopped, e) -> log.warn("{} {} batch stopped early (circuit breaker open): {} success, {} failed",
                        marketName, operation, stopped.successCount(), stopped.failCount()));
    }
}
