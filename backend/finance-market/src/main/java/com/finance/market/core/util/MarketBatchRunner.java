package com.finance.market.core.util;

import com.finance.common.util.BatchUpdateRunner;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;

public final class MarketBatchRunner {

    private MarketBatchRunner() {
    }

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
