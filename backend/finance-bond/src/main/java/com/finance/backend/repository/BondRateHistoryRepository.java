package com.finance.backend.repository;

import com.finance.backend.model.BondRateHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BondRateHistoryRepository extends JpaRepository<BondRateHistory, Long> {

    List<BondRateHistory> findByIsinCodeOrderByRateDateAsc(String isinCode);

    Optional<BondRateHistory> findTopByIsinCodeOrderByRateDateDesc(String isinCode);

    long countByIsinCode(String isinCode);

    boolean existsByIsinCodeAndRateDate(String isinCode, java.time.LocalDate rateDate);

    List<BondRateHistory> findByIsinCodeAndRateDateAfterOrderByRateDateAsc(
            String isinCode, java.time.LocalDate afterDate);
}
