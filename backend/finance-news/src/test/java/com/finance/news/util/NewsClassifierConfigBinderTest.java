package com.finance.news.util;

import com.finance.news.config.NewsClassifierProperties;
import com.finance.news.model.NewsCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NewsClassifierConfigBinderTest {

    private static final int DEFAULT_MIN_SCORE = 2;
    private static final int DEFAULT_CATEGORY_BONUS = 3;
    private static final int DEFAULT_SUBSTRING_THRESHOLD = 4;

    @AfterEach
    void restoreDefaults() {
        NewsCategoryResolver.overrideThresholds(DEFAULT_MIN_SCORE, DEFAULT_CATEGORY_BONUS);
        NewsTextMatcher.overrideSubstringMatchThreshold(DEFAULT_SUBSTRING_THRESHOLD);
    }

    @Test
    void should_applyMinScoreThresholdToResolver_when_overridesApplied() {
        // Arrange: a one-point score normally falls below the default min-score of 2 and yields null.
        NewsClassifierConfigBinder binder =
                new NewsClassifierConfigBinder(new NewsClassifierProperties(1, 3, 4));

        // Act
        binder.applyOverrides();

        // Assert
        NewsCategory result = NewsCategoryResolver.resolve(null,
                "Piyasalarda emtia sektöründe gelişmeler", null);
        assertThat(result).isEqualTo(NewsCategory.EMTIA);
    }

    @Test
    void should_applySubstringThresholdToMatcher_when_overridesApplied() {
        // Arrange
        NewsClassifierConfigBinder binder =
                new NewsClassifierConfigBinder(new NewsClassifierProperties(2, 3, 5));
        Set<String> tokens = new HashSet<>();
        tokens.add("testing");

        // Act
        binder.applyOverrides();

        // Assert
        assertThat(NewsTextMatcher.matchesKeyword("testing", tokens, "test")).isFalse();
    }

    @Test
    void should_substringMatchAtDefaultThreshold_when_restored() {
        // Arrange
        Set<String> tokens = new HashSet<>();
        tokens.add("testing");
        new NewsClassifierConfigBinder(new NewsClassifierProperties(2, 3, 4)).applyOverrides();

        // Act
        boolean matched = NewsTextMatcher.matchesKeyword("testing", tokens, "test");

        // Assert
        assertThat(matched).isTrue();
    }
}
