package com.finance.backend.service;

import com.finance.backend.client.NewsApiClient;
import com.finance.backend.dto.NewsApiResponse;
import com.finance.backend.entity.NewsArticle;
import com.finance.backend.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {
    
    private final NewsApiClient newsApiClient;
    private final NewsArticleRepository newsArticleRepository;
    
    public Page<NewsArticle> getAllNews(Pageable pageable) {
        return newsArticleRepository.findAllByOrderByPublishedAtDesc(pageable);
    }
    
    public Page<NewsArticle> getNewsByCategory(String category, Pageable pageable) {
        return newsArticleRepository.findByCategoryOrderByPublishedAtDesc(category, pageable);
    }
    
    public Optional<NewsArticle> getNewsById(Long id) {
        return newsArticleRepository.findById(id);
    }
    
    public Page<NewsArticle> getRecentNews(int days, Pageable pageable) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return newsArticleRepository.findByPublishedAtAfterOrderByPublishedAtDesc(since, pageable);
    }
    
    @Transactional
    @Scheduled(fixedRate = 43200000)
    @CacheEvict(value = "news", key = "'business-mixed'", beforeInvocation = true)
    public void fetchAndStoreNews() {
        log.info("Fetching news articles");
        
        try {
            NewsApiResponse response = newsApiClient.fetchBusinessNews();
            
            log.info("Received response from NewsAPI: {} articles", 
                    response != null && response.getArticles() != null ? response.getArticles().size() : 0);
            
            if (response == null || response.getArticles() == null) {
                log.warn("No articles received from NewsAPI");
                return;
            }
            
            int savedCount = 0;
            for (NewsApiResponse.Article article : response.getArticles()) {
                if (article.getUrl() == null) continue;
                
                Optional<NewsArticle> existing = newsArticleRepository.findByUrl(article.getUrl());
                if (existing.isPresent()) {
                    continue;
                }
                
                NewsArticle newsArticle = new NewsArticle();
                newsArticle.setTitle(article.getTitle());
                newsArticle.setDescription(article.getDescription());
                newsArticle.setContent(article.getContent());
                newsArticle.setUrl(article.getUrl());
                newsArticle.setImageUrl(article.getUrlToImage());
                newsArticle.setSource(article.getSource() != null ? article.getSource().getName() : "Unknown");
                newsArticle.setAuthor(article.getAuthor());
                newsArticle.setPublishedAt(parsePublishedDate(article.getPublishedAt()));
                newsArticle.setCategory(categorizeArticle(article));
                
                newsArticleRepository.save(newsArticle);
                savedCount++;
            }
            
            log.info("Saved {} new articles to database", savedCount);
            
        } catch (Exception e) {
            log.error("Error during scheduled news fetch: {}", e.getMessage(), e);
        }
    }
    
    private LocalDateTime parsePublishedDate(String publishedAt) {
        try {
            if (publishedAt != null) {
                return ZonedDateTime.parse(publishedAt, DateTimeFormatter.ISO_DATE_TIME).toLocalDateTime();
            }
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", publishedAt);
        }
        return LocalDateTime.now();
    }
    
    private String categorizeArticle(NewsApiResponse.Article article) {
        String text = (article.getTitle() + " " + article.getDescription()).toLowerCase();
        
        // Crypto keywords
        if (text.matches(".*(bitcoin|btc|ethereum|eth|crypto|cryptocurrency|blockchain|dogecoin|ripple|binance|coinbase).*")) {
            return "CRYPTO";
        }
        
        // Istanbul/Turkey stock market keywords
        if (text.matches(".*(borsa istanbul|bist|turkey stock|turkish stock|istanbul stock|xu100).*")) {
            return "ISTANBUL_STOCK";
        }
        
        // Forex and metals keywords
        if (text.matches(".*(forex|gold|silver|metal|currency|exchange rate|dollar|euro|pound|yen|precious metal).*")) {
            return "FOREX_METALS";
        }
        
        // US stock market keywords
        if (text.matches(".*(wall street|nasdaq|dow jones|s&p 500|nyse|stock market|shares|equity|aapl|msft|googl|tesla|amazon).*")) {
            return "US_STOCK";
        }
        
        return "GENERAL";
    }
}
