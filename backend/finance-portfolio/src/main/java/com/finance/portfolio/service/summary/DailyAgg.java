package com.finance.portfolio.service.summary;

import java.math.BigDecimal;

/**
 * Today's aggregate daily PnL ({@code amount}) and yesterday's value ({@code priorBaseline}, the
 * denominator for the daily %). Package-private so the summary service can read both fields after
 * {@link DailyAggregationService} computes them.
 */
record DailyAgg(BigDecimal amount, BigDecimal priorBaseline) {}
