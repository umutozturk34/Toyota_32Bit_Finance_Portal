package com.finance.market.forex.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.service.MarketHistoryProvider;
import com.finance.market.forex.dto.response.ForexCandleResponse;
import com.finance.market.forex.mapper.ForexResponseMapper;
import com.finance.market.forex.model.ForexCandle;
import com.finance.market.forex.repository.ForexCandleRepository;
import com.finance.market.forex.repository.ForexRepository;
import com.finance.shared.model.CandlePeriod;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Serves forex candle history (native TRY rates) for a currency over a preset period or explicit
 * range. A currency with no forex row yet (e.g. cold start before the market-data load) yields an
 * empty history rather than an error, so callers degrade cleanly instead of failing.
 */
@Log4j2
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ForexQueryService implements MarketHistoryProvider {

    private final ForexCandleRepository forexCandleRepository;
    private final ForexResponseMapper forexResponseMapper;
    private final ForexRepository forexRepository;

    @Override
    public MarketType getMarketType() {
        return MarketType.FOREX;
    }

    @Override
    public List<ForexCandleResponse> getHistory(String currencyCode, CandlePeriod period) {
        return loadCandles(currencyCode, period.toStartDateTime(), LocalDateTime.now());
    }

    @Override
    public List<ForexCandleResponse> getHistoryInRange(String currencyCode, LocalDate from, LocalDate to) {
        return loadCandles(currencyCode, from.atStartOfDay(), to.atTime(LocalTime.MAX));
    }

    private List<ForexCandleResponse> loadCandles(String currencyCode, LocalDateTime from, LocalDateTime to) {
        String normalized = currencyCode.strip().toUpperCase();
        if (!forexRepository.existsById(normalized)) {
            // No forex row yet (cold start before market data loads). A history query with no data is empty,
            // not a hard 404: throwing here from inside a caller's read transaction — portfolio valuation
            // fetches USD/EUR for the multi-currency frame — would mark that transaction rollback-only, so the
            // caught-and-degraded failure still fails the outer commit with UnexpectedRollbackException.
            log.debug("No forex row for {} yet — returning empty history", normalized);
            return List.of();
        }
        List<ForexCandle> candles = forexCandleRepository
                .findByCurrencyCodeAndCandleDateBetweenOrderByCandleDateAsc(normalized, from, to);
        return forexResponseMapper.toForexCandleResponses(candles);
    }
}
