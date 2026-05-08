package com.finance.market.fund.repository;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.market.core.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.market.core.scheduler.*;
import com.finance.common.event.*;
import com.finance.market.core.mapper.*;
import com.finance.common.repository.*;
import com.finance.market.core.client.*;

import com.finance.market.fund.model.FundCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FundCandleRepository extends JpaRepository<FundCandle, Long> {

    @Query("SELECT c.fundCode, MIN(c.candleDate), MAX(c.candleDate) FROM FundCandle c GROUP BY c.fundCode")
    List<Object[]> findCandleDateRangePerFund();

    @Query("SELECT c.fundCode, COUNT(c) FROM FundCandle c GROUP BY c.fundCode")
    List<Object[]> countCandlesPerFund();

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

    long countByFundCode(String fundCode);

    void deleteByFundCodeAndCandleDateBefore(String fundCode, LocalDateTime beforeDate);

    void deleteByCandleDateBefore(LocalDateTime beforeDate);
}
