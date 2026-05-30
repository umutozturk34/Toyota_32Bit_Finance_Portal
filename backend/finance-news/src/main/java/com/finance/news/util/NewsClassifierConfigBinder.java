package com.finance.news.util;

import com.finance.news.config.NewsClassifierProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** Pushes configured classifier thresholds into the static {@link NewsCategoryResolver}/{@link NewsTextMatcher} at startup. */
@Component
public class NewsClassifierConfigBinder {

    private final NewsClassifierProperties properties;

    public NewsClassifierConfigBinder(NewsClassifierProperties properties) {
        this.properties = properties;
    }

    /** Applies the configured min-score, default-category bonus, and substring-match threshold once the bean is ready. */
    @PostConstruct
    public void applyOverrides() {
        NewsCategoryResolver.overrideThresholds(properties.minScore(), properties.categoryBonus());
        NewsTextMatcher.overrideSubstringMatchThreshold(properties.substringMatchThreshold());
    }
}
