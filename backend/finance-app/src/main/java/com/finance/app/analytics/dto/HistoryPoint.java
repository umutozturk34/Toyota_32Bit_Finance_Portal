package com.finance.app.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One observation in an analytics series: a date and its value (price, rate, or portfolio value). */
public record HistoryPoint(LocalDate date, BigDecimal value) {}
