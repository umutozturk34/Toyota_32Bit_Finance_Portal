package com.finance.market.forex.repository;
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
import com.finance.market.forex.model.ForexCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
@Repository
public interface ForexCandleRepository extends JpaRepository<ForexCandle, Long> {
    List<ForexCandle> findByCurrencyCodeOrderByCandleDateAsc(String currencyCode);
    List<ForexCandle> findByCurrencyCodeOrderByCandleDateDesc(String currencyCode);
    List<ForexCandle> findByCurrencyCodeAndCandleDateBetweenOrderByCandleDateAsc(
        String currencyCode, LocalDateTime start, LocalDateTime end
    );
    Optional<ForexCandle> findFirstByCurrencyCodeOrderByCandleDateDesc(String currencyCode);
    Optional<ForexCandle> findByCurrencyCodeAndCandleDate(String currencyCode, LocalDateTime candleDate);

    List<ForexCandle> findByCurrencyCodeAndCandleDateIn(String currencyCode, Collection<LocalDateTime> candleDates);

    Long countByCurrencyCode(String currencyCode);

    List<ForexCandle> findTop2ByCurrencyCodeOrderByCandleDateDesc(String currencyCode);
}