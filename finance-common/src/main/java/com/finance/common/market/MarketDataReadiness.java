package com.finance.common.market;

/**
 * Cold-start readiness signal for market data. Implemented by the market-data initializer and consumed
 * by write paths (e.g. portfolio position creation) that must not run before prices/FX have loaded, so a
 * fresh-database request gets a clean "not ready" response instead of half-completing against missing data.
 */
public interface MarketDataReadiness {

    /** True once the cold-start market-data load has finished (always true on an already-populated database). */
    boolean isReady();
}
