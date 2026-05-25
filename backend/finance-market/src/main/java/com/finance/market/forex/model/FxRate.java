package com.finance.market.forex.model;

import com.finance.common.model.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FxRate(LocalDate date, Currency from, Currency to, BigDecimal rate) {

    public FxRate {
        if (date == null) {
            throw new IllegalArgumentException("FxRate date cannot be null");
        }
        if (from == null) {
            throw new IllegalArgumentException("FxRate from currency cannot be null");
        }
        if (to == null) {
            throw new IllegalArgumentException("FxRate to currency cannot be null");
        }
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("FxRate rate must be positive");
        }
    }
}
