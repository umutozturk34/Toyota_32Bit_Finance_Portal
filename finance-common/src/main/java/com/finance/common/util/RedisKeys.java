package com.finance.common.util;

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

    public static String marketSnapshotPrefix(String marketLabel) {
        return MARKET_PREFIX + marketLabel.toLowerCase() + SNAPSHOT_SUFFIX;
    }
}
