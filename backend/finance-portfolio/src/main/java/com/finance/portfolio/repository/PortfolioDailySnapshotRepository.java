package com.finance.portfolio.repository;

import com.finance.portfolio.model.PortfolioDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioDailySnapshotRepository extends JpaRepository<PortfolioDailySnapshot, Long> {

    boolean existsByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate snapshotDate);

    Optional<PortfolioDailySnapshot> findFirstByPortfolioIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
            Long portfolioId, LocalDateTime cutoff);

    Optional<PortfolioDailySnapshot> findFirstByPortfolioIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(
            Long portfolioId, LocalDateTime cutoff);

    Optional<PortfolioDailySnapshot> findFirstByPortfolioIdOrderByCreatedAtDesc(Long portfolioId);

    @Query("SELECT s.snapshotDate FROM PortfolioDailySnapshot s WHERE s.portfolioId = :pid AND s.snapshotDate BETWEEN :from AND :to")
    List<LocalDate> findExistingDates(@Param("pid") Long portfolioId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    List<PortfolioDailySnapshot> findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long portfolioId, LocalDate start, LocalDate end);

    List<PortfolioDailySnapshot> findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long portfolioId, LocalDateTime start, LocalDateTime end);

    void deleteByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate snapshotDate);

    void deleteByPortfolioIdAndSnapshotDateGreaterThanEqual(Long portfolioId, LocalDate from);
}
