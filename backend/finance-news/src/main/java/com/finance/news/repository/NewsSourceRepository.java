package com.finance.news.repository;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import com.finance.news.model.NewsSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NewsSourceRepository extends JpaRepository<NewsSource, Long> {

    List<NewsSource> findByEnabledTrueOrderBySortOrderAsc();

    List<NewsSource> findAllByOrderBySortOrderAsc();

    Optional<NewsSource> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
