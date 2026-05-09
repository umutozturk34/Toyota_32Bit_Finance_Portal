package com.finance.market.fund.model;

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
