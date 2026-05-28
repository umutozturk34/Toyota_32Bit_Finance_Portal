package com.finance.market.core.service;

import com.finance.common.model.MarketType;

/**
 * Per-market entry point for re-fetching live data, dispatched by {@link #getMarketType()}.
 */
public interface MarketRefresher {

    /** Market this refresher serves. */
    MarketType getMarketType();

    /** Refreshes every tracked asset in this market. */
    void refreshAll();

    /** Refreshes a single asset by code. */
    void refresh(String code);
}
