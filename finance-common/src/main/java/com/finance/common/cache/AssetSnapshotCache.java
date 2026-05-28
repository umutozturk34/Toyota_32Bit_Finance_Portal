package com.finance.common.cache;

import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.model.MarketType;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Read-through view over the latest market snapshots, keyed by {@link MarketType} and asset code.
 * Implementations return the most recently published price/metadata for an asset and never throw on
 * cache misses; an absent or unreadable entry yields an empty result rather than an error.
 */
public interface AssetSnapshotCache {

    Optional<AssetSnapshot> findByCode(MarketType type, String code);

    /**
     * Batch lookup; the returned map contains only codes that resolved to a snapshot,
     * so missing codes are simply omitted (never mapped to {@code null}).
     */
    Map<String, AssetSnapshot> findByCodes(MarketType type, Set<String> codes);
}
