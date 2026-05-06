package com.finance.common.service;

import com.finance.common.model.MarketType;

public interface MarketUpdatePort {

    void onMarketDataUpdated(MarketType type);
}
