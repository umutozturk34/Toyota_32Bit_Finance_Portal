package com.finance.news.repository;

import com.finance.news.model.NewsSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** News source persistence; enabled sources are returned in sort order for the scheduled ingest run. */
public interface NewsSourceRepository extends JpaRepository<NewsSource, Long> {

    List<NewsSource> findByEnabledTrueOrderBySortOrderAsc();

    List<NewsSource> findAllByOrderBySortOrderAsc();

    Optional<NewsSource> findByNameIgnoreCase(String name);
}
