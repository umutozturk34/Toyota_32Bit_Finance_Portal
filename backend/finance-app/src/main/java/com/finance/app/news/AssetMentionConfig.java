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

    /** A colloquial keyword → the asset it names (any type), with that asset's catalog code and type. Used both for
     *  commodity/currency words and for entity aliases (a company's common short name/acronym → its stock code). */
    public record KeywordRef(String keyword, String code, String type) {
    }

    /** Numeric tuning knobs for name/catalog matching. */
    public record Thresholds(int stockCoreMin, int cryptoNameMin, int fundNameMinChars, int fundNameMinWords, long catalogTtlMs) {
    }

    /** Immutable resolver config: commodity/currency keyword links, entity (company short-name/acronym) aliases,
     *  the common-word denylist, blocked tickers, name stopwords, thresholds. */
    public record MentionConfig(
            List<KeywordRef> commodityCurrencyKeywords,
            List<KeywordRef> entityAliases,
            Set<String> commonNameWords,
            Set<String> blockedTickers,
            Set<String> nameStopwords,
            Thresholds thresholds
    ) {
    }

    /** Mutable JSON binding form, converted to {@link MentionConfig} after loading. */
    public static class RawMentionConfig {
        public List<KeywordRef> commodityCurrencyKeywords;
        public List<KeywordRef> entityAliases;
        public List<String> commonNameWords;
        public List<String> blockedTickers;
        public List<String> nameStopwords;
        public Thresholds thresholds;
    }
}
