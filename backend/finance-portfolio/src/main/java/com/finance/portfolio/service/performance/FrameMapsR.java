package com.finance.portfolio.service.performance;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Aggregate per-currency frame over ALL lots (open + closed): cost@entry-FX, value (equity = cost +
 * PnL), the closed-portion realized cash, and total PnL — closed lots locked at their exit-date FX.
 */
record FrameMapsR(Map<String, BigDecimal> cost, Map<String, BigDecimal> value,
                  Map<String, BigDecimal> realized, Map<String, BigDecimal> pnl) {}
