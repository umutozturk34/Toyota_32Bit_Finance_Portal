package com.finance.market.core.service;

import java.math.BigDecimal;

public record ExchangeRateSnapshot(BigDecimal currentRate, BigDecimal previousRate) {

    public boolean isAvailable() {
        return currentRate != null;
    }
}
