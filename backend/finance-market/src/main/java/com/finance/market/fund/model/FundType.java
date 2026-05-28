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

    public abstract boolean scalesBulletinPrice();

    public abstract boolean scalesInvestorCount();
}
