package com.finance.portfolio.derivative.model;

import java.math.BigDecimal;

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

    public abstract BigDecimal pnlPerLot(BigDecimal entryPrice, BigDecimal exitPrice, BigDecimal contractSize);
}
