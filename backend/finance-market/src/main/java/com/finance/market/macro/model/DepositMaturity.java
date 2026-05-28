package com.finance.market.macro.model;

/** Deposit-rate maturity bucket (1/3/6/12 months, 12+ months, or total). */
public enum DepositMaturity {
    M1,
    M3,
    M6,
    M12,
    M12_PLUS,
    TOTAL
}
