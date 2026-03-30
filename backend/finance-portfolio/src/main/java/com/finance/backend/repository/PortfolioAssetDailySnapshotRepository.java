package com.finance.backend.repository;

import com.finance.backend.model.AssetType;
import com.finance.backend.model.PortfolioAssetDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PortfolioAssetDailySnapshotRepository extends JpaRepository<PortfolioAssetDailySnapshot, Long> {

    boolean existsByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate snapshotDate);

    List<PortfolioAssetDailySnapshot> findByPortfolioIdAndAssetTypeAndAssetCodeAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long portfolioId, AssetType assetType, String assetCode,
            LocalDate start, LocalDate end);

    void deleteByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate snapshotDate);
}
