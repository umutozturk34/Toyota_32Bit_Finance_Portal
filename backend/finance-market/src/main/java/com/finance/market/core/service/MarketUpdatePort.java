package com.finance.market.core.service;

import com.finance.common.model.MarketType;

public interface MarketUpdatePort {

    void onMarketDataUpdated(MarketType type);
}
