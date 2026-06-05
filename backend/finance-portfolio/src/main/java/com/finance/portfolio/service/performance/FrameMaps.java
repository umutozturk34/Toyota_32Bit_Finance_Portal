package com.finance.portfolio.service.performance;

import java.math.BigDecimal;
import java.util.Map;

/** Per-currency entry-date-FX cost basis and point-date value for the lots open at a given date. */
record FrameMaps(Map<String, BigDecimal> cost, Map<String, BigDecimal> value) {}
