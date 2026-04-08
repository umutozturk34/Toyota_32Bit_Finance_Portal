package com.finance.backend.util;

import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

public final class TxRunner {

    private TxRunner() {
    }

    public static <T> T run(TransactionTemplate transactionTemplate, Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    public static void runVoid(TransactionTemplate transactionTemplate, Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }
}
