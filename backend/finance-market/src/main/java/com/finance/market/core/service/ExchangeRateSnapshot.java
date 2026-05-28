package com.finance.market.core.service;

import java.math.BigDecimal;

/**
 * Current vs. previous spot rate for a currency pair; {@code previousRate} drives change display.
 */
public record ExchangeRateSnapshot(BigDecimal currentRate, BigDecimal previousRate) {

    /** True when a current rate is present and the snapshot is usable. */
    public boolean isAvailable() {
        return currentRate != null;
    }
}
