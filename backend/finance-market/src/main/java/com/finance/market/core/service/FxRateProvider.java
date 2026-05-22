package com.finance.market.core.service;

import com.finance.common.model.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.SortedMap;

public interface FxRateProvider {

    Optional<BigDecimal> rateAt(Currency from, Currency to, LocalDate date);

    SortedMap<LocalDate, BigDecimal> seriesAt(Currency from, Currency to, LocalDate fromDate, LocalDate toDate);
}
