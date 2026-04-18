package com.finance.backend.service;

import com.finance.backend.model.MarketType;

public interface CandleBatchRefresher {

    MarketType getMarketType();

    void refreshAll();
}
