package com.finance.market.commodity.repository;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import com.finance.market.commodity.model.CommodityCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommodityCandleRepository extends JpaRepository<CommodityCandle, Long> {

    List<CommodityCandle> findByCommodityCodeOrderByCandleDateAsc(String commodityCode);

    List<CommodityCandle> findByCommodityCodeAndCandleDateBetweenOrderByCandleDateAsc(
            String commodityCode, LocalDateTime start, LocalDateTime end);

    Optional<CommodityCandle> findFirstByCommodityCodeOrderByCandleDateDesc(String commodityCode);

    List<CommodityCandle> findByCommodityCodeAndCandleDateIn(String commodityCode, Collection<LocalDateTime> candleDates);

    Long countByCommodityCode(String commodityCode);

    int deleteByCandleDateBefore(LocalDateTime cutoffDate);

    List<CommodityCandle> findTop2ByCommodityCodeOrderByCandleDateDesc(String commodityCode);
}
