package com.finance.backend.mapper;

import com.finance.backend.dto.external.NewsArticleDto;
import com.finance.backend.dto.internal.RssArticleData;
import com.finance.backend.model.NewsArticle;
import com.finance.backend.model.NewsCategory;
import com.finance.backend.util.NewsCategoryResolver;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public abstract class NewsArticleMapper {

    public NewsArticleDto toDto(RssArticleData data, String sourceName, String sourceUrl, String defaultCategory) {
        NewsCategory category = NewsCategoryResolver.resolve(
                defaultCategory, data.title(), data.description());

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
}
