package com.finance.backend.service;

import com.finance.backend.model.MarketType;

public interface SnapshotBatchRefresher {

    MarketType getMarketType();

    void refreshAll();
}
