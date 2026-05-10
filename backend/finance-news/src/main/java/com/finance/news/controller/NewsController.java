package com.finance.news.controller;


import com.finance.common.config.AppProperties;
import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.shared.dto.response.GroupCount;
import com.finance.news.dto.response.NewsArticleDetailResponse;
import com.finance.news.dto.response.NewsArticleResponse;
import com.finance.common.dto.response.PagedResponse;
import com.finance.news.service.article.NewsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
public class NewsController {

    private final AppProperties appProperties;
    private final NewsQueryService newsQueryService;
    private final Translator translator;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PagedResponse<NewsArticleResponse>> getNews(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        AppProperties.NewsPage pagination = appProperties.getPagination().getNews();
        int resolvedSize = size == null ? pagination.getDefaultSize() : size;
        resolvedSize = Math.max(1, Math.min(resolvedSize, pagination.getMaxSize()));
        PagedResponse<NewsArticleResponse> result = newsQueryService.search(
                category, search, sort, direction, page, resolvedSize);
        return ApiResponse.success(translator.translate("api.news.listRetrieved"), result);
    }

    @GetMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GroupCount>> getCategoryCounts() {
        return ApiResponse.success(translator.translate("api.news.categoriesRetrieved"), newsQueryService.getCategoryCounts());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<NewsArticleDetailResponse> getNewsById(@PathVariable Long id) {
        return ApiResponse.success(translator.translate("api.news.retrieved"),
                newsQueryService.getById(id));
    }
}
