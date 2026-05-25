package com.finance.market.core.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.model.Currency;

import java.time.LocalDate;

public class FxRateUnavailableException extends BusinessException {

    public FxRateUnavailableException(Currency from, Currency to, LocalDate date) {
        super("error.fx.rateUnavailable", from, to, date);
    }
}
