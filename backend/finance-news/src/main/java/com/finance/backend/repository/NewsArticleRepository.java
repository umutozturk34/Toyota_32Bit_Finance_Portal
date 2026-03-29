package com.finance.backend.repository;

import com.finance.backend.model.NewsArticle;
import com.finance.backend.model.NewsCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    boolean existsByLink(String link);

    boolean existsByGuid(String guid);

    List<NewsArticle> findByCategoryOrderByPublishedAtDesc(NewsCategory category);

    @Query("SELECT n FROM NewsArticle n WHERE n.category = :category ORDER BY n.publishedAt DESC LIMIT :limit")
    List<NewsArticle> findTopByCategoryOrderByPublishedAtDesc(@Param("category") NewsCategory category, @Param("limit") int limit);

    @Query("SELECT n FROM NewsArticle n ORDER BY n.publishedAt DESC LIMIT :limit")
    List<NewsArticle> findLatest(@Param("limit") int limit);

    void deleteByPublishedAtBefore(LocalDateTime cutoff);
}
