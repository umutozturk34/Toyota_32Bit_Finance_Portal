package com.finance.stock.repository;
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
import com.finance.stock.model.StockCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
@Repository
public interface StockCandleRepository extends JpaRepository<StockCandle, Long> {
    List<StockCandle> findByStockSymbolOrderByCandleDateDesc(String stockSymbol);
    List<StockCandle> findByStockSymbolOrderByCandleDateAsc(String stockSymbol);
    List<StockCandle> findByStockSymbolAndCandleDateBetweenOrderByCandleDateAsc(
        String stockSymbol,
        LocalDateTime startDate,
        LocalDateTime endDate
    );
    Optional<StockCandle> findFirstByStockSymbolOrderByCandleDateDesc(String stockSymbol);
    List<StockCandle> findTop2ByStockSymbolOrderByCandleDateDesc(String stockSymbol);
    Optional<StockCandle> findByStockSymbolAndCandleDate(String stockSymbol, LocalDateTime candleDate);

    List<StockCandle> findByStockSymbolAndCandleDateIn(String stockSymbol, Collection<LocalDateTime> candleDates);

    List<StockCandle> findTop1825ByStockSymbolOrderByCandleDateDesc(String stockSymbol);
    long countByStockSymbol(String stockSymbol);
    void deleteByStockSymbolAndCandleDateBefore(String stockSymbol, LocalDateTime beforeDate);
    void deleteByCandleDateBefore(LocalDateTime beforeDate);
}
