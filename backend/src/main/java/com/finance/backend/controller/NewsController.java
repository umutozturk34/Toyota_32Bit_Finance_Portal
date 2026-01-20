package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.entity.NewsArticle;
import com.finance.backend.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {
    
    private final NewsService newsService;
    
    @GetMapping
    public ResponseEntity<ApiResponse<Page<NewsArticle>>> getAllNews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<NewsArticle> news = newsService.getAllNews(pageable);
        
        return ResponseEntity.ok(ApiResponse.success("News retrieved successfully", news));
    }
    
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<Page<NewsArticle>>> getNewsByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<NewsArticle> news = newsService.getNewsByCategory(category.toUpperCase(), pageable);
        
        return ResponseEntity.ok(ApiResponse.success("News retrieved successfully", news));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NewsArticle>> getNewsById(@PathVariable Long id) {
        return newsService.getNewsById(id)
                .map(news -> ResponseEntity.ok(ApiResponse.success("News found", news)))
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<Page<NewsArticle>>> getRecentNews(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<NewsArticle> news = newsService.getRecentNews(days, pageable);
        
        return ResponseEntity.ok(ApiResponse.success("Recent news retrieved successfully", news));
    }
    
    @PostMapping("/fetch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> fetchNews() {
        newsService.fetchAndStoreNews();
        return ResponseEntity.ok(ApiResponse.success("News fetch started", "News fetch triggered"));
    }
}
