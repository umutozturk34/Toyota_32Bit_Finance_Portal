package com.finance.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.news")
public class NewsProperties {

    private int maxArticlesPerSource = 50;
    private int cacheTtlHours = 24;
    private int defaultCategoryLimit = 20;
    private Map<String, Integer> categoryLimits = new HashMap<>();
}
