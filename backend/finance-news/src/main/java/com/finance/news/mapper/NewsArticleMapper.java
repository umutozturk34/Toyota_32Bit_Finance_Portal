package com.finance.news.mapper;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

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

@Mapper(componentModel = "spring")
public abstract class NewsArticleMapper {

    @Autowired
    protected NewsProperties newsProperties;

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
