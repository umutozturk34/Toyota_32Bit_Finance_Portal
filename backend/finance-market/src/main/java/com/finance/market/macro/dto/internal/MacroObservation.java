package com.finance.market.macro.dto.internal;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MacroObservation(LocalDate observedAt, BigDecimal value) { }
