package com.finance.market.core.service;

import com.finance.common.model.MarketType;

public interface MarketRefresher {

    MarketType getMarketType();

    void refreshAll();

    void refresh(String code);
}
