package com.finance.backend.service;

import com.finance.backend.model.MarketType;

public interface PortfolioSnapshotPort {
    void onMarketUpdate(MarketType marketType);
}
