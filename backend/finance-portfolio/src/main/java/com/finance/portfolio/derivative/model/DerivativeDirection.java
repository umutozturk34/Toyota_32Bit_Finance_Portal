package com.finance.portfolio.derivative.model;

import java.math.BigDecimal;

/**
 * Side of a derivative position. Encapsulates the per-lot PnL sign: {@code LONG} profits when price
 * rises ({@code (exit−entry)×size}), {@code SHORT} when it falls (reversed).
 */
public enum DerivativeDirection {
    LONG {
        @Override
        public BigDecimal pnlPerLot(BigDecimal entryPrice, BigDecimal exitPrice, BigDecimal contractSize) {
            if (entryPrice == null || exitPrice == null) return null;
            BigDecimal size = contractSize != null ? contractSize : BigDecimal.ONE;
            return exitPrice.subtract(entryPrice).multiply(size);
        }
    },
    SHORT {
        @Override
        public BigDecimal pnlPerLot(BigDecimal entryPrice, BigDecimal exitPrice, BigDecimal contractSize) {
            if (entryPrice == null || exitPrice == null) return null;
            BigDecimal size = contractSize != null ? contractSize : BigDecimal.ONE;
            return entryPrice.subtract(exitPrice).multiply(size);
        }
    };

    /** PnL for one lot in the price currency; null when either price is missing, size defaults to 1. */
    public abstract BigDecimal pnlPerLot(BigDecimal entryPrice, BigDecimal exitPrice, BigDecimal contractSize);
}
