package com.finance.news.mapper;

import com.finance.news.dto.response.NewsArticleDetailResponse;
import com.finance.news.dto.response.NewsArticleResponse;
import com.finance.news.dto.response.NewsAssetResponse;
import com.finance.news.model.NewsArticle;
import com.finance.news.model.NewsArticleAsset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** MapStruct mapper from article entities to list/detail API responses; the category enum is exposed as its name. */
@Mapper(componentModel = "spring")
public abstract class NewsResponseMapper {

    @Mapping(target = "category", expression = "java(article.getCategory().name())")
    @Mapping(target = "sourceName", source = "sourceName")
    public abstract NewsArticleResponse toResponse(NewsArticle article);

    public abstract List<NewsArticleResponse> toResponses(List<NewsArticle> articles);

    @Mapping(target = "category", expression = "java(article.getCategory().name())")
    public abstract NewsArticleDetailResponse toDetailResponse(NewsArticle article);

    /** Each mentioned asset (the {@code assets} set on both responses maps through this element mapping). */
    @Mapping(target = "code", source = "assetCode")
    @Mapping(target = "type", source = "assetType")
    public abstract NewsAssetResponse toAssetResponse(NewsArticleAsset asset);
}
