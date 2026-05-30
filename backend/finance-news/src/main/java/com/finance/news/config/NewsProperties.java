package com.finance.news.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * News ingest and query settings: per-source article cap, cache TTL, per-category list limits, and
 * mapping thresholds that decide when feed content is treated as rich HTML vs a short description.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.news")
public class NewsProperties {

    private int maxArticlesPerSource = 50;
    private int cacheTtlHours = 24;
    private int defaultCategoryLimit = 20;
    private Map<String, Integer> categoryLimits = new HashMap<>();
    private Mapping mapping = new Mapping();

    @Getter
    @Setter
    public static class Mapping {
        private int richHtmlMinLength = 150;
        private int shortDescriptionThreshold = 80;
    }
}
