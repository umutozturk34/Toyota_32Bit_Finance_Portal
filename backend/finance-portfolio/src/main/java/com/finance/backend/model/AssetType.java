package com.finance.backend.model;

import com.finance.backend.config.AppProperties;

import java.math.BigDecimal;

public enum AssetType {
    CRYPTO(MarketType.CRYPTO) {
        @Override
        public BigDecimal commissionRate(AppProperties.Commission commission) {
            return commission.getCryptoRate();
        }
    },
    STOCK(MarketType.STOCK) {
        @Override
        public BigDecimal commissionRate(AppProperties.Commission commission) {
            return commission.getStockRate();
        }
    },
    FOREX(MarketType.FOREX) {
        @Override
        public BigDecimal commissionRate(AppProperties.Commission commission) {
            return BigDecimal.ZERO;
        }
    },
    FUND(MarketType.FUND) {
        @Override
        public BigDecimal commissionRate(AppProperties.Commission commission) {
            return commission.getFundRate();
        }
    };

    private final MarketType marketType;

    AssetType(MarketType marketType) {
        this.marketType = marketType;
    }

    public MarketType marketType() {
        return marketType;
    }

    public abstract BigDecimal commissionRate(AppProperties.Commission commission);
}
