package com.finance.market.macro.dto.internal;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Internal value object for a single macroeconomic data point: the {@code value}
 * reported for an indicator on the {@code observedAt} date.
 */
public record MacroObservation(LocalDate observedAt, BigDecimal value) { }
