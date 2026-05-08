package com.finance.news.mapper;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import com.finance.news.dto.response.NewsArticleDetailResponse;
import com.finance.news.dto.response.NewsArticleResponse;
import com.finance.news.model.NewsArticle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class NewsResponseMapper {

    @Mapping(target = "category", expression = "java(article.getCategory().name())")
    @Mapping(target = "sourceName", source = "sourceName")
    public abstract NewsArticleResponse toResponse(NewsArticle article);

    public abstract List<NewsArticleResponse> toResponses(List<NewsArticle> articles);

    @Mapping(target = "category", expression = "java(article.getCategory().name())")
    public abstract NewsArticleDetailResponse toDetailResponse(NewsArticle article);
}
