package com.finance.portfolio.service;

import java.math.BigDecimal;

record DailyDelta(BigDecimal amount, BigDecimal percent) {
    static final DailyDelta EMPTY = new DailyDelta(null, null);
}
