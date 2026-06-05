package com.finance.notification.reports.fx;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One forex selling-rate observation (lira per unit of foreign currency) on a given trading day.
 * Points form an ASC-sorted series that {@link ReportFxConverter} forward-fills when converting.
 */
public record ForexRatePoint(LocalDate date, BigDecimal rate) {}
