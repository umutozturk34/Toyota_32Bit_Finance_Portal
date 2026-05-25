package com.finance.news.util;

import com.finance.news.config.NewsClassifierProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class NewsClassifierConfigBinder {

    private final NewsClassifierProperties properties;

    public NewsClassifierConfigBinder(NewsClassifierProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void applyOverrides() {
        NewsCategoryResolver.overrideThresholds(properties.minScore(), properties.categoryBonus());
        NewsTextMatcher.overrideSubstringMatchThreshold(properties.substringMatchThreshold());
    }
}
