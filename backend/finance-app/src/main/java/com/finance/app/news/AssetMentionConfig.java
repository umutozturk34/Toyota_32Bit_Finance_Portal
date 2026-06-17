package com.finance.app.news;

import java.util.List;
import java.util.Set;

/**
 * Configuration types for {@link AssetMentionResolverImpl}: the parsed, immutable runtime config and its mutable raw
 * JSON binding shape. Keeps the resolver's keyword links, denylists and tuning thresholds in
 * {@code asset-mention-keywords.json} rather than hard-coded in the resolver, so they can be curated without a code
 * change — the same approach the news classifier uses for {@code news-category-keywords.json}.
 */
public final class AssetMentionConfig {

    private AssetMentionConfig() {
    }

    /** A colloquial Turkish keyword (stored already normalised) → the commodity/currency it names, and that asset's type. */
    public record KeywordRef(String keyword, String code, String type) {
    }

    /** Numeric tuning knobs for name/catalog matching. */
    public record Thresholds(int stockCoreMin, int cryptoNameMin, int fundNameMinChars, int fundNameMinWords, long catalogTtlMs) {
    }

    /** Immutable resolver config: commodity/currency keyword links, the common-word denylist, blocked tickers, name stopwords, thresholds. */
    public record MentionConfig(
            List<KeywordRef> commodityCurrencyKeywords,
            Set<String> commonNameWords,
            Set<String> blockedTickers,
            Set<String> nameStopwords,
            Thresholds thresholds
    ) {
    }

    /** Mutable JSON binding form, converted to {@link MentionConfig} after loading. */
    public static class RawMentionConfig {
        public List<KeywordRef> commodityCurrencyKeywords;
        public List<String> commonNameWords;
        public List<String> blockedTickers;
        public List<String> nameStopwords;
        public Thresholds thresholds;
    }
}
