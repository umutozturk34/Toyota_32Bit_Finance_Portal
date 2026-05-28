package com.finance.market.core.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.model.Currency;

import java.time.LocalDate;

/**
 * Raised when no FX rate exists for a currency pair on/before a requested date, blocking a
 * conversion that cannot be made point-in-time accurate.
 */
public class FxRateUnavailableException extends BusinessException {

    public FxRateUnavailableException(Currency from, Currency to, LocalDate date) {
        super("error.fx.rateUnavailable", from, to, date);
    }
}
