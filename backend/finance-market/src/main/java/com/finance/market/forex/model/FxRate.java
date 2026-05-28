package com.finance.market.forex.model;

import com.finance.common.model.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable, validated {@code from->to} FX rate for a specific date; rejects null fields and
 * non-positive rates at construction so downstream code can trust it.
 */
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
