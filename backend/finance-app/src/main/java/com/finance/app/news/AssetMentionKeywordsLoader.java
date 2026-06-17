package com.finance.app.news;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Loads {@link AssetMentionConfig.MentionConfig} from the bundled {@code asset-mention-keywords.json} resource so the
 * asset-mention resolver's keyword/denylist/threshold data lives in configuration rather than in code. Mirrors
 * {@code NewsCategoryConfigLoader}: read once at class init, fail loud if the resource is missing or malformed.
 */
public final class AssetMentionKeywordsLoader {

    private static final String RESOURCE = "asset-mention-keywords.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AssetMentionKeywordsLoader() {
    }

    /** Reads and parses the keyword config; throws {@link IllegalStateException} if missing or unreadable. */
    public static AssetMentionConfig.MentionConfig load() {
        try (InputStream in = AssetMentionKeywordsLoader.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Asset-mention keyword config not found: " + RESOURCE);
            }
            AssetMentionConfig.RawMentionConfig raw =
                    OBJECT_MAPPER.readValue(in, AssetMentionConfig.RawMentionConfig.class);
            return toConfig(raw);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read asset-mention keyword config: " + RESOURCE, e);
        }
    }

    private static AssetMentionConfig.MentionConfig toConfig(AssetMentionConfig.RawMentionConfig raw) {
        return new AssetMentionConfig.MentionConfig(
                raw.commodityCurrencyKeywords == null ? List.of() : List.copyOf(raw.commodityCurrencyKeywords),
                toSet(raw.commonNameWords),
                toSet(raw.blockedTickers),
                toSet(raw.nameStopwords),
                raw.thresholds
        );
    }

    private static Set<String> toSet(List<String> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }
}
