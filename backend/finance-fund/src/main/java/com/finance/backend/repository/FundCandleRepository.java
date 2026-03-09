package com.finance.backend.repository;

import com.finance.backend.model.FundCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FundCandleRepository extends JpaRepository<FundCandle, Long> {

    List<FundCandle> findByFundCodeOrderByCandleDateDesc(String fundCode);

    List<FundCandle> findByFundCodeOrderByCandleDateAsc(String fundCode);

    List<FundCandle> findByFundCodeAndCandleDateBetweenOrderByCandleDateAsc(
        String fundCode,
        LocalDateTime startDate,
        LocalDateTime endDate
    );

    Optional<FundCandle> findFirstByFundCodeOrderByCandleDateDesc(String fundCode);

    Optional<FundCandle> findByFundCodeAndCandleDate(String fundCode, LocalDateTime candleDate);

    List<FundCandle> findTop1825ByFundCodeOrderByCandleDateDesc(String fundCode);

    long countByFundCode(String fundCode);

    void deleteByFundCodeAndCandleDateBefore(String fundCode, LocalDateTime beforeDate);

    void deleteByCandleDateBefore(LocalDateTime beforeDate);
}
