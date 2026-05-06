package com.finance.portfolio.repository;
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

import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioAssetDailySnapshotRepository extends JpaRepository<PortfolioAssetDailySnapshot, Long> {

    boolean existsByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate snapshotDate);

    Optional<PortfolioAssetDailySnapshot> findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
            Long portfolioId, AssetType assetType, String assetCode, LocalDateTime cutoff);

    Optional<PortfolioAssetDailySnapshot> findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtGreaterThanOrderByCreatedAtAsc(
            Long portfolioId, AssetType assetType, String assetCode, LocalDateTime cutoff);

    @Query("""
            SELECT s FROM PortfolioAssetDailySnapshot s
            WHERE s.portfolioId = :pid
              AND s.id IN (
                  SELECT MAX(t.id) FROM PortfolioAssetDailySnapshot t
                  WHERE t.portfolioId = :pid
                  GROUP BY t.assetType, t.assetCode
              )
            """)
    List<PortfolioAssetDailySnapshot> findLatestPerAsset(@Param("pid") Long portfolioId);

    @Query("SELECT DISTINCT s.snapshotDate FROM PortfolioAssetDailySnapshot s WHERE s.portfolioId = :pid AND s.snapshotDate BETWEEN :from AND :to")
    List<LocalDate> findExistingDates(@Param("pid") Long portfolioId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    List<PortfolioAssetDailySnapshot> findByPortfolioIdAndAssetTypeAndAssetCodeAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long portfolioId, AssetType assetType, String assetCode,
            LocalDate start, LocalDate end);

    List<PortfolioAssetDailySnapshot> findByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate snapshotDate);

    List<PortfolioAssetDailySnapshot> findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long portfolioId, LocalDateTime start, LocalDateTime end);

    List<PortfolioAssetDailySnapshot> findByPortfolioIdAndAssetTypeAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long portfolioId, AssetType assetType, LocalDateTime start, LocalDateTime end);

    List<PortfolioAssetDailySnapshot> findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long portfolioId, AssetType assetType, String assetCode,
            LocalDateTime start, LocalDateTime end);

    void deleteByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate snapshotDate);

    void deleteByPortfolioIdAndAssetTypeAndSnapshotDate(Long portfolioId, AssetType assetType, LocalDate snapshotDate);

    void deleteByPortfolioIdAndSnapshotDateGreaterThanEqual(Long portfolioId, LocalDate from);
}
