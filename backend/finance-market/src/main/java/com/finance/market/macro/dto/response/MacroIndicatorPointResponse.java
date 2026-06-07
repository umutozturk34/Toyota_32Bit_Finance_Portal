package com.finance.market.macro.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single point in a macro indicator's time series: the observed value on a given date.
 *
 * @param observedAt date the value was observed/published
 * @param value      the indicator's numeric value at that date (unit depends on the indicator)
 */
public record MacroIndicatorPointResponse(LocalDate observedAt, BigDecimal value) { }
