package com.finance.market.core.service;

import com.finance.common.model.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.SortedMap;

/**
 * Date-accurate FX rate source. Implementations must use the rate of the requested date (or the
 * closest prior date) and never a future date, so historical valuations stay point-in-time correct.
 */
public interface FxRateProvider {

    /** {@code from->to} rate at the given date, empty when none is available within policy. */
    Optional<BigDecimal> rateAt(Currency from, Currency to, LocalDate date);

    /** {@code from->to} rates over the inclusive {@code [fromDate, toDate]} window. */
    SortedMap<LocalDate, BigDecimal> seriesAt(Currency from, Currency to, LocalDate fromDate, LocalDate toDate);
}
