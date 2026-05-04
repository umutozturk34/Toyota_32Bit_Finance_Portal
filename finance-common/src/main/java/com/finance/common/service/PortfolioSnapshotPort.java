package com.finance.common.service;

import com.finance.common.model.MarketType;

public interface PortfolioSnapshotPort {
    void onMarketUpdate(MarketType marketType);
}
