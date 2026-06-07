package com.finance.market.fund.model;

/**
 * TEFAS fund category: BYF (exchange-traded) vs. YAT (other investment funds). Each declares which
 * numeric fields are scaled, since the two categories report different field sets.
 */
public enum FundType {
    BYF {
        @Override
        public boolean scalesBulletinPrice() {
            return true;
        }

        @Override
        public boolean scalesInvestorCount() {
            return false;
        }
    },
    YAT {
        @Override
        public boolean scalesBulletinPrice() {
            return false;
        }

        @Override
        public boolean scalesInvestorCount() {
            return true;
        }
    };

    /**
     * Whether the raw bulletin price for this fund category is reported in a scaled form and must be
     * unscaled before use. True for {@link #BYF}, false for {@link #YAT}.
     */
    public abstract boolean scalesBulletinPrice();

    /**
     * Whether the raw investor count for this fund category is reported in a scaled form and must be
     * unscaled before use. True for {@link #YAT}, false for {@link #BYF}.
     */
    public abstract boolean scalesInvestorCount();
}
