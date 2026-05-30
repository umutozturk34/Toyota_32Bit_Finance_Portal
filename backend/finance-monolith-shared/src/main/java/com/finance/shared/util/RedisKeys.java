package com.finance.shared.util;

/**
 * Central registry of Redis key names and prefixes shared across modules, keeping cache key
 * conventions in one place so producers and consumers cannot drift apart.
 */
public final class RedisKeys {

    private static final String MARKET_PREFIX = "market:";
    private static final String SNAPSHOT_SUFFIX = ":snapshot:";

    public static final String TOP_MOVERS = MARKET_PREFIX + "topMovers";
    public static final String INDICES_FIELD = "INDICES";
    public static final String GAINERS_SUFFIX = ":GAINERS";
    public static final String LOSERS_SUFFIX = ":LOSERS";
    public static final String NEWS_ARTICLE = "news:article:";

    private RedisKeys() {
    }

    /** Key prefix under which a market's per-asset snapshot hashes are stored (market label lowercased). */
    public static String marketSnapshotPrefix(String marketLabel) {
        return MARKET_PREFIX + marketLabel.toLowerCase() + SNAPSHOT_SUFFIX;
    }
}
