package com.finance.portfolio.service.performance;

import java.math.BigDecimal;

/** Cumulative realized PnL and closed cost basis for the lots closed up to a given snapshot date. */
record RealizedToDate(BigDecimal realized, BigDecimal closedCost) {}
