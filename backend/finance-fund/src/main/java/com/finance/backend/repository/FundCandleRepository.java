package com.finance.backend.repository;

import com.finance.backend.model.FundCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FundCandleRepository extends JpaRepository<FundCandle, Long> {

    @Query("SELECT DISTINCT CAST(c.candleDate AS LocalDate) FROM FundCandle c "
            + "WHERE c.fundCode = :fundCode "
            + "AND c.candleDate >= :from AND c.candleDate <= :to")
    List<LocalDate> findCandleDates(@Param("fundCode") String fundCode,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);


    List<FundCandle> findByFundCodeOrderByCandleDateDesc(String fundCode);

    List<FundCandle> findByFundCodeOrderByCandleDateAsc(String fundCode);

    List<FundCandle> findByFundCodeAndCandleDateBetweenOrderByCandleDateAsc(
        String fundCode,
        LocalDateTime startDate,
        LocalDateTime endDate
    );

    Optional<FundCandle> findFirstByFundCodeOrderByCandleDateDesc(String fundCode);

    Optional<FundCandle> findFirstByFundCodeAndCandleDateBeforeOrderByCandleDateDesc(String fundCode, LocalDateTime before);

    Optional<FundCandle> findByFundCodeAndCandleDate(String fundCode, LocalDateTime candleDate);

    List<FundCandle> findByFundCodeAndCandleDateIn(String fundCode, Collection<LocalDateTime> candleDates);

    List<FundCandle> findTop1825ByFundCodeOrderByCandleDateDesc(String fundCode);

    List<FundCandle> findTop2ByFundCodeOrderByCandleDateDesc(String fundCode);

    long countByFundCode(String fundCode);

    void deleteByFundCodeAndCandleDateBefore(String fundCode, LocalDateTime beforeDate);

    void deleteByCandleDateBefore(LocalDateTime beforeDate);
}
