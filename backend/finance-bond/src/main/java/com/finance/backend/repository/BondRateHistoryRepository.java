package com.finance.backend.repository;

import com.finance.backend.model.BondRateHistory;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BondRateHistoryRepository extends JpaRepository<BondRateHistory, Long> {

    List<BondRateHistory> findByIsinCodeOrderByRateDateAsc(String isinCode);

    Optional<BondRateHistory> findTopByIsinCodeOrderByRateDateDesc(String isinCode);

    long countByIsinCode(String isinCode);

    boolean existsByIsinCodeAndRateDate(String isinCode, LocalDate rateDate);

    List<BondRateHistory> findByIsinCodeAndRateDateAfterOrderByRateDateAsc(
            String isinCode, LocalDate afterDate);
}
