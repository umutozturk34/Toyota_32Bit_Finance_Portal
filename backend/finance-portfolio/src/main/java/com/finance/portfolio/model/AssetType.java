package com.finance.portfolio.model;
import com.finance.common.model.MarketType;


import com.finance.common.config.CommissionProperties;

import java.math.BigDecimal;

public enum AssetType {
    CRYPTO(MarketType.CRYPTO) {
        @Override
        public BigDecimal commissionRate(CommissionProperties commission) {
            return commission.getCryptoRate();
        }
    },
    STOCK(MarketType.STOCK) {
        @Override
        public BigDecimal commissionRate(CommissionProperties commission) {
            return commission.getStockRate();
        }
    },
    FOREX(MarketType.FOREX) {
        @Override
        public BigDecimal commissionRate(CommissionProperties commission) {
            return BigDecimal.ZERO;
        }
    },
    FUND(MarketType.FUND) {
        @Override
        public BigDecimal commissionRate(CommissionProperties commission) {
            return commission.getFundRate();
        }
    },
    COMMODITY(MarketType.COMMODITY) {
        @Override
        public BigDecimal commissionRate(CommissionProperties commission) {
            return commission.getCommodityRate();
        }
    };

    private final MarketType marketType;

    AssetType(MarketType marketType) {
        this.marketType = marketType;
    }

    public MarketType marketType() {
        return marketType;
    }

    public abstract BigDecimal commissionRate(CommissionProperties commission);
}
