package com.finance.app.analytics.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A single point on a scenario series: the invested amount's value (in target currency) on a date. */
public record ScenarioPoint(LocalDate date, BigDecimal value) {}
