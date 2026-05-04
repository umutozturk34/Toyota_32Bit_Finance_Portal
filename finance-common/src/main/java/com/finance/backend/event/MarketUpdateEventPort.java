package com.finance.backend.event;

import com.finance.backend.model.MarketType;

public interface MarketUpdateEventPort {

    void publishMarketUpdated(MarketType marketType, String source);
}
