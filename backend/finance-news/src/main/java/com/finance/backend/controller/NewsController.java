package com.finance.backend.controller;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.response.GroupCount;
import com.finance.backend.dto.response.NewsArticleDetailResponse;
import com.finance.backend.dto.response.NewsArticleResponse;
import com.finance.backend.dto.response.PagedResponse;
import com.finance.backend.service.NewsQueryService;
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
        return ApiResponse.success("News retrieved successfully", result);
    }

    @GetMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GroupCount>> getCategoryCounts() {
        return ApiResponse.success("News categories retrieved", newsQueryService.getCategoryCounts());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<NewsArticleDetailResponse> getNewsById(@PathVariable Long id) {
        return ApiResponse.success("News article retrieved successfully",
                newsQueryService.getById(id));
    }
}
