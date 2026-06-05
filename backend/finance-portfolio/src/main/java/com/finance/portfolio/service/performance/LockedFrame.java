package com.finance.portfolio.service.performance;

import java.math.BigDecimal;

/** Closed-lot contribution in one currency: cost@entry-FX, realized & total PnL locked at exit-FX. */
record LockedFrame(BigDecimal cost, BigDecimal realized, BigDecimal pnl) {
    static final LockedFrame EMPTY = new LockedFrame(null, null, null);
}
