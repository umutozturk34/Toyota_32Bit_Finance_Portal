package com.finance.news.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
