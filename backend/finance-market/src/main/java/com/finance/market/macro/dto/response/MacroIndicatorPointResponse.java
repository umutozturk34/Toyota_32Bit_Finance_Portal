package com.finance.market.macro.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MacroIndicatorPointResponse(LocalDate observedAt, BigDecimal value) { }
