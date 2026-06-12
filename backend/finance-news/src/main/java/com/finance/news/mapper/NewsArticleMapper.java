package com.finance.news.mapper;

import com.finance.news.config.NewsProperties;
import com.finance.news.dto.external.NewsArticleDto;
import com.finance.news.dto.internal.RssArticleData;
import com.finance.news.model.NewsArticle;
import com.finance.news.model.NewsCategory;
import com.finance.news.util.NewsCategoryResolver;
import com.finance.news.util.NewsTextUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

/**
 * Builds persistable article DTOs from raw feed data: runs the classifier and drops articles that
 * cannot be categorized or carry no description/content.
 */
@Mapper(componentModel = "spring")
public abstract class NewsArticleMapper {

    private NewsProperties newsProperties;

    @Autowired
    protected void setNewsProperties(NewsProperties newsProperties) {
        this.newsProperties = newsProperties;
    }

    /** Classifies and attributes a raw feed article; returns {@code null} to skip uncategorizable or empty articles. */
    public NewsArticleDto toDto(RssArticleData data, String sourceName, String sourceUrl, String defaultCategory) {
        String resolverDescription = buildResolverDescription(data.description(), data.content());

        NewsCategory category = NewsCategoryResolver.resolve(
                defaultCategory, data.title(), resolverDescription);

        if (category == null) {
            return null;
        }

        if (isBlank(data.description()) && isBlank(data.content())) {
            return null;
        }

        return new NewsArticleDto(
                data.title(),
                data.link(),
                data.description(),
                data.content(),
                sourceName,
                sourceUrl,
                category,
                data.publishedAt(),
                data.imageUrl(),
                data.guid()
        );
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fetchedAt", source = "now")
    public abstract NewsArticle toEntity(NewsArticleDto dto, LocalDateTime now);

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Picks the richest text for classification: a long-enough description, else stripped content (optionally appended). */
    private String buildResolverDescription(String description, String content) {
        if (description != null && description.length() >= newsProperties.getMapping().getShortDescriptionThreshold()) {
            return description;
        }

        if (content != null && !content.isBlank()) {
            String stripped = NewsTextUtils.stripHtmlTags(content);
            if (description != null) {
                return description + " " + stripped;
            }
            return stripped;
        }

        return description;
    }
}
