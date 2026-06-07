package com.finance.shared.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RedisKeysTest {

    @ParameterizedTest
    @CsvSource({
            "STOCK,market:stock:snapshot:",
            "Crypto,market:crypto:snapshot:",
            "forex,market:forex:snapshot:"
    })
    void should_lowercaseLabelAndWrapInPrefixSuffix_when_buildingSnapshotPrefix(String label, String expected) {
        // Arrange + Act
        String prefix = RedisKeys.marketSnapshotPrefix(label);

        // Assert
        assertThat(prefix).isEqualTo(expected);
    }

    @Test
    void should_exposeStableTopMoversKey() {
        // Arrange + Act + Assert
        assertThat(RedisKeys.TOP_MOVERS).isEqualTo("market:topMovers");
    }

    @Test
    void should_exposeStableNewsArticlePrefix() {
        // Arrange + Act + Assert
        assertThat(RedisKeys.NEWS_ARTICLE).isEqualTo("news:article:");
    }
}
