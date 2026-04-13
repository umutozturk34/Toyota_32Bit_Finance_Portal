package com.finance.backend.repository;

import com.finance.backend.model.AssetType;
import com.finance.backend.model.PortfolioTransaction;
import com.finance.backend.model.TransactionSide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, Long>, JpaSpecificationExecutor<PortfolioTransaction> {

    List<PortfolioTransaction> findByPortfolioIdOrderByCreatedAtDesc(Long portfolioId);

    List<PortfolioTransaction> findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long portfolioId, LocalDateTime start, LocalDateTime end);

    List<PortfolioTransaction> findByPortfolioIdAndAssetTypeAndSide(
            Long portfolioId, AssetType assetType, TransactionSide side);
}
