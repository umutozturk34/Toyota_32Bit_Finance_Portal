package com.finance.portfolio.derivative.repository;

import com.finance.portfolio.derivative.model.DerivativePosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DerivativePositionRepository extends JpaRepository<DerivativePosition, Long> {

    List<DerivativePosition> findByPortfolioId(Long portfolioId);

    @Query("select dp from DerivativePosition dp where dp.portfolio.id = :portfolioId and dp.closeDate is null")
    List<DerivativePosition> findOpenByPortfolio(@Param("portfolioId") Long portfolioId);

    @Query("select dp from DerivativePosition dp " +
            "where dp.closeDate is null and dp.viopContract.expiryDate < :today")
    List<DerivativePosition> findOpenWithExpiredContract(@Param("today") LocalDate today);

    Optional<DerivativePosition> findByIdAndPortfolioId(Long id, Long portfolioId);
}
