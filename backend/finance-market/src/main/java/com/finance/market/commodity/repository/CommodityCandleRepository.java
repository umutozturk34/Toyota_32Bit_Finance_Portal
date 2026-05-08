package com.finance.market.commodity.repository;
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
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;

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
