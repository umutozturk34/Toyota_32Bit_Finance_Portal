package com.finance.app.news;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the bundled asset-mention keyword config parses and carries the curated keywords, denylist, tickers and thresholds. */
class AssetMentionKeywordsLoaderTest {

    @Test
    void load_parsesBundledConfig_withCuratedKeywordsDenylistTickersAndThresholds() {
        // Arrange + Act
        AssetMentionConfig.MentionConfig config = AssetMentionKeywordsLoader.load();

        // Assert: "brent" links Brent; bare "petrol" is intentionally NOT a keyword (it lives in the denylist instead).
        assertThat(config.commodityCurrencyKeywords())
                .anyMatch(k -> k.keyword().equals("brent") && k.code().equals("BZ=F") && k.type().equals("COMMODITY"))
                .anyMatch(k -> k.keyword().equals("dolar") && k.code().equals("USD") && k.type().equals("FOREX"))
                .noneMatch(k -> k.keyword().equals("petrol"));
        assertThat(config.commonNameWords()).contains("platform", "federal", "petrol", "turizm");
        assertThat(config.blockedTickers()).contains("FED", "TCMB", "SPK", "ECB");
        assertThat(config.nameStopwords()).contains("ve");
        assertThat(config.thresholds().stockCoreMin()).isEqualTo(5);
        assertThat(config.thresholds().cryptoNameMin()).isEqualTo(3);
        assertThat(config.thresholds().catalogTtlMs()).isEqualTo(3_600_000L);
    }
}
