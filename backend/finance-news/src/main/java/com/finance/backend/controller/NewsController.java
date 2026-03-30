package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.response.NewsArticleDetailResponse;
import com.finance.backend.dto.response.NewsArticleResponse;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.mapper.NewsResponseMapper;
import com.finance.backend.model.NewsArticle;
import com.finance.backend.model.NewsCategory;
import com.finance.backend.service.NewsCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsCacheService newsCacheService;
    private final NewsResponseMapper newsResponseMapper;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<NewsArticleResponse>>> getLatestNews() {
        List<NewsArticleResponse> articles = newsResponseMapper.toResponses(
                newsCacheService.getLatest());
        return ResponseEntity.ok(ApiResponse.success("Latest news retrieved successfully", articles));
    }

    @GetMapping("/category/{category}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<NewsArticleResponse>>> getNewsByCategory(
            @PathVariable NewsCategory category) {
        List<NewsArticleResponse> articles = newsResponseMapper.toResponses(
                newsCacheService.getByCategory(category));
        return ResponseEntity.ok(ApiResponse.success("News retrieved successfully", articles));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NewsArticleDetailResponse>> getNewsById(@PathVariable Long id) {
        NewsArticle article = newsCacheService.getById(id)
                .orElseThrow(() -> new BusinessException("News article not found: " + id));
        return ResponseEntity.ok(ApiResponse.success("News article retrieved successfully",
                newsResponseMapper.toDetailResponse(article)));
    }
}
