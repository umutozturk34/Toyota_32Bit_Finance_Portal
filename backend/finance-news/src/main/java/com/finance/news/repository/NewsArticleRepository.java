package com.finance.news.repository;

import com.finance.news.model.NewsArticle;
import com.finance.news.model.NewsCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** Article persistence with dedup lookups (link/guid), category-scoped feeds, retention purge, and category counts. */
@Repository
public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long>, JpaSpecificationExecutor<NewsArticle> {

    boolean existsByLink(String link);

    boolean existsByGuid(String guid);

    List<NewsArticle> findByCategoryOrderByPublishedAtDesc(NewsCategory category);

    @Query("SELECT n FROM NewsArticle n WHERE n.category = :category ORDER BY n.publishedAt DESC LIMIT :limit")
    List<NewsArticle> findTopByCategoryOrderByPublishedAtDesc(@Param("category") NewsCategory category, @Param("limit") int limit);

    @Query("SELECT n FROM NewsArticle n ORDER BY n.publishedAt DESC LIMIT :limit")
    List<NewsArticle> findLatest(@Param("limit") int limit);

    void deleteByPublishedAtBefore(LocalDateTime cutoff);

    /** Bulk-deletes all articles belonging to a source (used when a source is removed); returns the count purged. */
    @Modifying
    @Query("DELETE FROM NewsArticle n WHERE n.source.id = :sourceId")
    int deleteBySourceId(@Param("sourceId") Long sourceId);

    /** Returns {@code [category, count]} pairs for non-null categories, ordered by category. */
    @Query("SELECT n.category, COUNT(n) FROM NewsArticle n WHERE n.category IS NOT NULL GROUP BY n.category ORDER BY n.category")
    List<Object[]> countByCategory();
}
