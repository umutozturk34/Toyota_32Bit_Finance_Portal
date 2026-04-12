package com.finance.backend.service;

import com.finance.backend.model.MarketType;

public interface MarketUpdatePort {

    void onMarketDataUpdated(MarketType type);
}
