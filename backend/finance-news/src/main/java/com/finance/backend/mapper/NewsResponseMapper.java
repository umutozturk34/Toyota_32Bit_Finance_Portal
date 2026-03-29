package com.finance.backend.mapper;

import com.finance.backend.dto.response.NewsArticleResponse;
import com.finance.backend.model.NewsArticle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class NewsResponseMapper {

    @Mapping(target = "category", expression = "java(article.getCategory().name())")
    public abstract NewsArticleResponse toResponse(NewsArticle article);

    public abstract List<NewsArticleResponse> toResponses(List<NewsArticle> articles);
}
