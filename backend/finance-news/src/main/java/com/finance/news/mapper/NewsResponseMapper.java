package com.finance.news.mapper;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.event.*;
import com.finance.common.repository.*;

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
