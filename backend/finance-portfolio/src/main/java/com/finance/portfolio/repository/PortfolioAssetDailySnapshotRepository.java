package com.finance.portfolio.repository;

import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for per-asset {@link PortfolioAssetDailySnapshot} rows. Beyond range reads, it provides
 * "latest per asset" / "latest per tracked asset before cutoff" lookups used for daily-delta baselines
 * and aggregation, plus scoped deletes for backfill recomputation.
 */
@Repository
public interface PortfolioAssetDailySnapshotRepository extends JpaRepository<PortfolioAssetDailySnapshot, Long> {

    boolean existsByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate snapshotDate);

    Optional<PortfolioAssetDailySnapshot> findFirstByPortfolioIdAndTrackedAssetIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
            Long portfolioId, Long trackedAssetId, LocalDateTime cutoff);

    Optional<PortfolioAssetDailySnapshot> findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long portfolioId, AssetType assetType, String assetCode, LocalDateTime cutoff);

    /** Most recent row before {@code cutoff} for each given tracked asset (used to seed backfill daily deltas). */
    @Query("""
            SELECT s FROM PortfolioAssetDailySnapshot s
            WHERE s.portfolioId = :pid
              AND s.trackedAsset.id IN :trackedIds
              AND s.createdAt <= :cutoff
              AND s.id IN (
                  SELECT MAX(t.id) FROM PortfolioAssetDailySnapshot t
                  WHERE t.portfolioId = :pid
                    AND t.trackedAsset.id IN :trackedIds
                    AND t.createdAt <= :cutoff
                  GROUP BY t.trackedAsset.id
              )
            """)
    List<PortfolioAssetDailySnapshot> findLatestPerTrackedAssetBefore(
            @Param("pid") Long portfolioId,
            @Param("trackedIds") Collection<Long> trackedIds,
            @Param("cutoff") LocalDateTime cutoff);

    Optional<PortfolioAssetDailySnapshot> findFirstByPortfolioIdAndTrackedAssetIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(
            Long portfolioId, Long trackedAssetId, LocalDateTime cutoff);

    /** The single most recent row per (asset type, asset code) for the portfolio — its current per-asset state. */
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

    List<PortfolioAssetDailySnapshot> findByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate snapshotDate);

    List<PortfolioAssetDailySnapshot> findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long portfolioId, LocalDateTime start, LocalDateTime end);

    List<PortfolioAssetDailySnapshot> findByPortfolioIdAndAssetTypeAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long portfolioId, AssetType assetType, LocalDateTime start, LocalDateTime end);

    List<PortfolioAssetDailySnapshot> findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long portfolioId, AssetType assetType, String assetCode, LocalDateTime start, LocalDateTime end);

    List<PortfolioAssetDailySnapshot> findByPortfolioIdAndTrackedAssetIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long portfolioId, Long trackedAssetId,
            LocalDateTime start, LocalDateTime end);

    void deleteByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate snapshotDate);

    void deleteByPortfolioIdAndAssetTypeAndSnapshotDate(Long portfolioId, AssetType assetType, LocalDate snapshotDate);

    void deleteByPortfolioIdAndAssetTypeAndAssetCode(Long portfolioId, AssetType assetType, String assetCode);

    void deleteByPortfolioIdAndSnapshotDateGreaterThanEqual(Long portfolioId, LocalDate from);

    void deleteByPortfolioIdAndAssetTypeAndAssetCodeAndSnapshotDateGreaterThanEqual(
            Long portfolioId, AssetType assetType, String assetCode, LocalDate from);

    void deleteByPortfolioIdAndAssetTypeAndAssetCodeAndSnapshotDateBetween(
            Long portfolioId, AssetType assetType, String assetCode, LocalDate from, LocalDate to);

    void deleteByPortfolioId(Long portfolioId);

    List<PortfolioAssetDailySnapshot> findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long portfolioId, LocalDate from, LocalDate to);
}
