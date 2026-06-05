package com.finance.portfolio.service.performance;

import java.math.BigDecimal;
import java.util.Map;

/** A single asset type's combined open+closed per-currency frame: value, cost, realized, and total PnL. */
record TypeFrames(Map<String, BigDecimal> value, Map<String, BigDecimal> cost,
                  Map<String, BigDecimal> realized, Map<String, BigDecimal> pnl) {}
