package com.finance.market.core.service;

/**
 * Fetches and persists the latest snapshot for a single asset, used when adding/refreshing an
 * individual instrument rather than a whole market.
 */
public interface MarketSnapshotProcessor {

    /** Fetches and stores the current snapshot for the given code. */
    void refreshOne(String code);

    /** Whether the asset is known to the upstream source (validates a code before tracking). */
    boolean exists(String code);
}
