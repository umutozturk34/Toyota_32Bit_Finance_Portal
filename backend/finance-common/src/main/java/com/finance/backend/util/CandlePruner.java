package com.finance.backend.util;

import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;

public final class CandlePruner {

    private CandlePruner() {
    }

    public static void pruneByYears(TransactionTemplate transactionTemplate,
                                    ZoneId zone,
                                    int years,
                                    Consumer<LocalDateTime> pruneAction) {
        LocalDateTime cutoffDate = LocalDateTime.now(zone).minusYears(years);
        transactionTemplate.executeWithoutResult(status -> pruneAction.accept(cutoffDate));
    }

    public static void pruneByYears(TransactionTemplate transactionTemplate,
                                    int years,
                                    Consumer<LocalDateTime> pruneAction) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusYears(years);
        transactionTemplate.executeWithoutResult(status -> pruneAction.accept(cutoffDate));
    }

    public static void pruneByDays(TransactionTemplate transactionTemplate,
                                   int daysToKeep,
                                   Consumer<LocalDateTime> pruneAction) {
        LocalDateTime cutoffDate = LocalDateTime.now()
                .truncatedTo(ChronoUnit.DAYS)
                .minusDays(daysToKeep - 1L);
        transactionTemplate.executeWithoutResult(status -> pruneAction.accept(cutoffDate));
    }
}
