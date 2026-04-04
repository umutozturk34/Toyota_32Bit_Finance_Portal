package com.finance.backend.repository;

import com.finance.backend.model.AssetType;
import com.finance.backend.model.PortfolioAssetDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PortfolioAssetDailySnapshotRepository extends JpaRepository<PortfolioAssetDailySnapshot, Long> {

    boolean existsByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate snapshotDate);

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
}
