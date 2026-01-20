package com.finance.backend.repository;

import com.finance.backend.entity.NewsArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {
    
    Page<NewsArticle> findAllByOrderByPublishedAtDesc(Pageable pageable);
    
    Page<NewsArticle> findByCategoryOrderByPublishedAtDesc(String category, Pageable pageable);
    
    Optional<NewsArticle> findByUrl(String url);
    
    Page<NewsArticle> findByPublishedAtAfterOrderByPublishedAtDesc(LocalDateTime date, Pageable pageable);
}
