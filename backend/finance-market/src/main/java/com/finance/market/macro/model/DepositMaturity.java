package com.finance.market.macro.model;

/** Deposit-rate maturity bucket (1/3/6/12 months, 12+ months, or total). */
public enum DepositMaturity {
    M1("1M"),
    M3("3M"),
    M6("6M"),
    M12("1Y"),
    M12_PLUS("1Y+"),
    TOTAL("Total");

    private final String tenorLabel;

    DepositMaturity(String tenorLabel) {
        this.tenorLabel = tenorLabel;
    }

    /** Short human label for the tenor (e.g. "3M", "1Y", "1Y+"); used to build deposit display names. */
    public String tenorLabel() {
        return tenorLabel;
    }
}
