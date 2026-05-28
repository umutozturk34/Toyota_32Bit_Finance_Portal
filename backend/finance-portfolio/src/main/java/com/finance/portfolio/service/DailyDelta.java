package com.finance.portfolio.service;

import java.math.BigDecimal;

/** Day-over-day change of a snapshot: PnL amount in TRY and its percentage; {@link #EMPTY} when no prior baseline exists. */
record DailyDelta(BigDecimal amount, BigDecimal percent) {
    static final DailyDelta EMPTY = new DailyDelta(null, null);
}
