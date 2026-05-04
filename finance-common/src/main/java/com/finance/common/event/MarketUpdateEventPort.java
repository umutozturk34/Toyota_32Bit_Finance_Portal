package com.finance.common.event;

import com.finance.common.model.MarketType;

public interface MarketUpdateEventPort {

    void publishMarketUpdated(MarketType marketType, String source);
}
