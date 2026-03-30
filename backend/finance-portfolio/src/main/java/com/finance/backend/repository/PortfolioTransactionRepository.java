package com.finance.backend.repository;

import com.finance.backend.model.PortfolioTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, Long> {

    List<PortfolioTransaction> findByPortfolioIdOrderByCreatedAtDesc(Long portfolioId);
}
