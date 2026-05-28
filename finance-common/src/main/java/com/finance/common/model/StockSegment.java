package com.finance.common.model;

/**
 * Classifies a tracked stock as a primary index constituent, a secondary index constituent, or a
 * plain equity. Drives index-membership and compare-only defaults in {@link TrackedAssetType}.
 */
public enum StockSegment {
    MAIN_INDEX,
    SECONDARY_INDEX,
    EQUITY
}
