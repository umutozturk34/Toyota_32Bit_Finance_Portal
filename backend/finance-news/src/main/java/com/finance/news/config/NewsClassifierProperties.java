package com.finance.news.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the keyword classifier: minimum score to assign a category, bonus for matching a
 * source's default category, and the keyword length above which substring (vs token) matching applies.
 */
@ConfigurationProperties(prefix = "app.news.classifier")
public record NewsClassifierProperties(
        Integer minScore,
        Integer categoryBonus,
        Integer substringMatchThreshold
) {

    public NewsClassifierProperties {
        if (minScore == null) minScore = 2;
        if (categoryBonus == null) categoryBonus = 3;
        if (substringMatchThreshold == null) substringMatchThreshold = 4;
    }
}
