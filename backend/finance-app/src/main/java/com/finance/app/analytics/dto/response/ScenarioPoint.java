package com.finance.app.analytics.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ScenarioPoint(LocalDate date, BigDecimal value) {}
