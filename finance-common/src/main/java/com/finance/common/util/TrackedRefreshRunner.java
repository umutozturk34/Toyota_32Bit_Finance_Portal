package com.finance.common.util;

import org.apache.logging.log4j.Logger;

import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class TrackedRefreshRunner {

    private TrackedRefreshRunner() {
    }

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
