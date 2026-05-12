package com.finance.market.forex.service;

import com.finance.common.exception.ResourceNotFoundException;
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
            throw new ResourceNotFoundException("error.market.forexNotFound", normalized);
        }
        List<ForexCandle> candles = forexCandleRepository
                .findByCurrencyCodeAndCandleDateBetweenOrderByCandleDateAsc(normalized, from, to);
        return forexResponseMapper.toForexCandleResponses(candles);
    }
}
