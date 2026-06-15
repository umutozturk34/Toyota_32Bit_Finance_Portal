package com.finance.app.service.overview;

import tools.jackson.databind.JsonNode;
import com.finance.app.dto.response.overview.NewsData;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import com.finance.common.dto.response.PagedResponse;
import com.finance.news.dto.response.NewsArticleResponse;
import com.finance.news.service.article.NewsQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides the NEWS widget: latest articles, optionally constrained to configured categories. With multiple
 * categories it interleaves them round-robin and dedupes by article id so no single category dominates, up
 * to the requested count.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class NewsWidgetProvider implements OverviewWidgetProvider {

    private static final String SORT_FIELD = "publishedAt";
    private static final String SORT_DIRECTION = "desc";

    private final NewsQueryService newsQueryService;
    private final OverviewDefaults defaults;

    @Override
    public WidgetKind kind() {
        return WidgetKind.NEWS;
    }

    @Override
    public NewsData fetch(String userSub, WidgetSection section) {
        int count = readCount(section);
        List<String> categories = readCategories(section);
        List<NewsArticleResponse> aggregated = aggregate(categories, count);
        return new NewsData(categories, aggregated.stream().map(this::toRow).toList());
    }

    private List<NewsArticleResponse> aggregate(List<String> categories, int count) {
        if (categories.isEmpty()) {
            return newsQueryService.search(null, null, null, SORT_FIELD, SORT_DIRECTION, 0, count).content();
        }
        Map<String, List<NewsArticleResponse>> perCategory = new LinkedHashMap<>();
        for (String category : categories) {
            try {
                PagedResponse<NewsArticleResponse> page = newsQueryService.search(category, null, null, SORT_FIELD, SORT_DIRECTION, 0, count);
                perCategory.put(category, new ArrayList<>(page.content()));
            } catch (RuntimeException ex) {
                log.warn("NewsWidget skip category {} reason={}", category, ex.getMessage());
                perCategory.put(category, List.of());
            }
        }
        LinkedHashMap<Long, NewsArticleResponse> deduped = new LinkedHashMap<>();
        int round = 0;
        while (deduped.size() < count) {
            boolean addedThisRound = false;
            for (List<NewsArticleResponse> articles : perCategory.values()) {
                if (round >= articles.size()) continue;
                NewsArticleResponse article = articles.get(round);
                if (deduped.putIfAbsent(article.id(), article) == null) {
                    addedThisRound = true;
                    if (deduped.size() >= count) break;
                }
            }
            if (!addedThisRound) break;
            round++;
        }
        return new ArrayList<>(deduped.values());
    }

    private int readCount(WidgetSection section) {
        JsonNode node = section.config().get("count");
        if (node == null || !node.isInt() || node.asInt() <= 0) return defaults.defaultNewsCount();
        return Math.min(node.asInt(), defaults.maxNewsItems());
    }

    private List<String> readCategories(WidgetSection section) {
        JsonNode node = section.config().get("categories");
        if (node == null || !node.isArray()) return List.of();
        List<String> categories = new ArrayList<>(node.size());
        for (JsonNode entry : node) {
            String value = entry.asString(null);
            if (value != null && !value.isBlank()) categories.add(value);
        }
        return categories;
    }

    private NewsData.NewsRow toRow(NewsArticleResponse article) {
        OffsetDateTime publishedAt = article.publishedAt() != null
                ? article.publishedAt().atOffset(ZoneOffset.UTC)
                : null;
        return new NewsData.NewsRow(
                article.id(), article.title(), article.category(),
                article.imageUrl(), article.sourceName(), publishedAt);
    }
}
