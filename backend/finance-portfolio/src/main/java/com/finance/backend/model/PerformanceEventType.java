package com.finance.backend.model;

public enum PerformanceEventType {
    BUY,
    SELL,
    MARKET_UP,
    MARKET_DOWN;

    public static PerformanceEventType fromTransactionSide(TransactionSide side) {
        return switch (side) {
            case BUY -> BUY;
            case SELL -> SELL;
        };
    }
}
