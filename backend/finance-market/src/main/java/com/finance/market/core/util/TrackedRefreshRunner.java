package com.finance.market.core.util;

import org.apache.logging.log4j.Logger;

import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** Normalizes a code and runs a snapshot refresh, logging only when the refresh reports success. */
public final class TrackedRefreshRunner {

    private TrackedRefreshRunner() {
    }

    /** Refreshes the normalized code's snapshot, skipping blanks and logging a successful refresh. */
    public static void refreshSnapshot(String code,
                                       UnaryOperator<String> normalizer,
                                       Predicate<String> refresh,
                                       Logger log,
                                       String marketName) {
        String normalized = normalizer.apply(code);
        if (normalized == null || normalized.isBlank()) {
            return;
        }
        if (refresh.test(normalized)) {
            log.info("Refreshed tracked {} snapshot for {}", marketName, normalized);
        }
    }
}
