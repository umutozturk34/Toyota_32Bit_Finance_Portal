package com.finance.backend.repository;

import com.finance.backend.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {
    
    List<ExchangeRate> findByRateDateOrderByCurrencyCodeAsc(LocalDate rateDate);
    
    Optional<ExchangeRate> findByCurrencyCodeAndRateDate(String currencyCode, LocalDate rateDate);
    
    @Query("SELECT e FROM ExchangeRate e WHERE e.rateDate = (SELECT MAX(e2.rateDate) FROM ExchangeRate e2) ORDER BY e.currencyCode ASC")
    List<ExchangeRate> findLatestRates();
    
    List<ExchangeRate> findByCurrencyCodeOrderByRateDateDesc(String currencyCode);
}
