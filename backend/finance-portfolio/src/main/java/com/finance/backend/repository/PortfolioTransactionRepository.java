package com.finance.backend.repository;

import com.finance.backend.model.PortfolioTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, Long> {

    List<PortfolioTransaction> findByPortfolioIdOrderByCreatedAtDesc(Long portfolioId);

    List<PortfolioTransaction> findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long portfolioId, LocalDateTime start, LocalDateTime end);
}
