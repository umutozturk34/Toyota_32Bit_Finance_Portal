package com.finance.news.repository;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;

import com.finance.news.model.NewsArticle;
import com.finance.news.model.NewsCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

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

    @Query("SELECT n.category, COUNT(n) FROM NewsArticle n WHERE n.category IS NOT NULL GROUP BY n.category ORDER BY n.category")
    List<Object[]> countByCategory();
}
