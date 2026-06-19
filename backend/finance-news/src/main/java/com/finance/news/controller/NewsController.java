package com.finance.news.controller;


import com.finance.common.config.AppProperties;
import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.shared.dto.response.GroupCount;
import com.finance.news.dto.response.NewsArticleDetailResponse;
import com.finance.news.dto.response.NewsArticleResponse;
import com.finance.news.dto.response.NewsAssetCountsResponse;
import com.finance.common.dto.response.PagedResponse;
import com.finance.news.service.article.NewsQueryService;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Authenticated read API for news: paged/filtered article list, per-category counts, and single-article detail. */
@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
@Validated
public class NewsController {

    private final AppProperties appProperties;
    private final NewsQueryService newsQueryService;
    private final Translator translator;

    /** Lists articles filtered by category/search and sorted; page size defaults and is clamped to the configured max. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PagedResponse<NewsArticleResponse>> getNews(
            @RequestParam(required = false) @Size(max = 100) String category,
            @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(required = false) @Size(max = 512) String assetCode,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        AppProperties.NewsPage pagination = appProperties.getPagination().getNews();
        int resolvedSize = size == null ? pagination.getDefaultSize() : size;
        resolvedSize = Math.max(1, Math.min(resolvedSize, pagination.getMaxSize()));
        PagedResponse<NewsArticleResponse> result = newsQueryService.search(
                category, search, assetCode, sort, direction, page, resolvedSize);
        return ApiResponse.success(translator.translate("api.news.listRetrieved"), result);
    }

    /** The available news categories with each one's article count — powers the category filter rail. */
    @GetMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GroupCount>> getCategoryCounts() {
        return ApiResponse.success(translator.translate("api.news.categoriesRetrieved"), newsQueryService.getCategoryCounts());
    }

    /** The most-mentioned assets across all news with their article counts — powers the "filter by asset" rail. */
    @GetMapping("/assets")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<NewsAssetCountsResponse> getAssetCounts(
            @RequestParam(defaultValue = "24") int limit) {
        int resolved = Math.max(1, Math.min(limit, 60));
        return ApiResponse.success(translator.translate("api.news.categoriesRetrieved"),
                newsQueryService.getAssetCounts(resolved));
    }

    /** Full detail of a single article by id, including body and mentioned-asset data. */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<NewsArticleDetailResponse> getNewsById(@PathVariable Long id) {
        return ApiResponse.success(translator.translate("api.news.retrieved"),
                newsQueryService.getById(id));
    }
}
