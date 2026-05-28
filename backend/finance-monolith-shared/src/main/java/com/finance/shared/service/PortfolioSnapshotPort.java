package com.finance.shared.service;

import com.finance.common.model.MarketType;

/**
 * Reverse seam letting the market module notify the portfolio module that fresh prices landed,
 * without depending on it. Implemented by the portfolio snapshot service, which reacts by
 * recomputing snapshots for positions in the affected market.
 */
public interface PortfolioSnapshotPort {

    /** Signals that pricing for the given market has been refreshed. */
    void onMarketUpdate(MarketType marketType);
}
