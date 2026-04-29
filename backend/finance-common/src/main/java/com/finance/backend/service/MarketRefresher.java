package com.finance.backend.service;

import com.finance.backend.model.MarketType;

public interface MarketRefresher {

    MarketType getMarketType();

    void refreshAll();

    void refresh(String code);
}
