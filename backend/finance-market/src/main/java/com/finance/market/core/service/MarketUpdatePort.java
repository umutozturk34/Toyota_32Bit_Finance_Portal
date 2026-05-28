package com.finance.market.core.service;

import com.finance.common.model.MarketType;

/**
 * Hook invoked after a market's data is refreshed so downstream concerns (caches, derived
 * aggregates) can react.
 */
public interface MarketUpdatePort {

    void onMarketDataUpdated(MarketType type);
}
