package com.finance.news.repository;

import com.finance.news.model.NewsArticle;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Article persistence with dedup lookups (link/guid), category-scoped feeds, retention purge, and category counts. */
public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long>, JpaSpecificationExecutor<NewsArticle> {

    boolean existsByLink(String link);

    boolean existsByGuid(String guid);

    /** Bulk-deletes all articles belonging to a source (used when a source is removed); returns the count purged. */
    @Modifying
    @Query("DELETE FROM NewsArticle n WHERE n.source.id = :sourceId")
    int deleteBySourceId(@Param("sourceId") Long sourceId);

    /** Returns {@code [category, count]} pairs for non-null categories, ordered by category. */
    @Query("SELECT n.category, COUNT(n) FROM NewsArticle n WHERE n.category IS NOT NULL GROUP BY n.category ORDER BY n.category")
    List<Object[]> countByCategory();

    /**
     * Articles with no resolved asset links yet, id-ascending after {@code afterId} — a forward cursor for the
     * one-time backfill so each unenriched article is visited exactly once (no-mention rows don't re-loop).
     */
    @Query("SELECT a FROM NewsArticle a WHERE a.assets IS EMPTY AND a.id > :afterId ORDER BY a.id ASC")
    List<NewsArticle> findWithoutAssetsAfter(@Param("afterId") Long afterId, Pageable pageable);
}
