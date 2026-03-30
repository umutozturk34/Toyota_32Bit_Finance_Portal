package com.finance.backend.repository;

import com.finance.backend.model.PortfolioDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PortfolioDailySnapshotRepository extends JpaRepository<PortfolioDailySnapshot, Long> {

    boolean existsByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate snapshotDate);

    List<PortfolioDailySnapshot> findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long portfolioId, LocalDate start, LocalDate end);

    void deleteByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate snapshotDate);
}
