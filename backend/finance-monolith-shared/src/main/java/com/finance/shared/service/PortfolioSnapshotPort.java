package com.finance.shared.service;

import com.finance.common.model.MarketType;

public interface PortfolioSnapshotPort {
    void onMarketUpdate(MarketType marketType);
}
