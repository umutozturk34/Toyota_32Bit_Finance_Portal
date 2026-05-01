package com.finance.backend.service;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.mapper.ForexResponseMapper;
import com.finance.backend.model.CandlePeriod;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.repository.ForexCandleRepository;
import com.finance.backend.repository.ForexRepository;
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
    public List<CandleResponse> getHistory(String currencyCode, CandlePeriod period) {
        return loadCandles(currencyCode, period.toStartDateTime(), LocalDateTime.now());
    }

    @Override
    public List<CandleResponse> getHistoryInRange(String currencyCode, LocalDate from, LocalDate to) {
        return loadCandles(currencyCode, from.atStartOfDay(), to.atTime(LocalTime.MAX));
    }

    private List<CandleResponse> loadCandles(String currencyCode, LocalDateTime from, LocalDateTime to) {
        String normalized = currencyCode.strip().toUpperCase();
        if (!forexRepository.existsById(normalized)) {
            throw new ResourceNotFoundException("Forex not found: " + normalized);
        }
        List<ForexCandle> candles = forexCandleRepository
                .findByCurrencyCodeAndCandleDateBetweenOrderByCandleDateAsc(normalized, from, to);
        return forexResponseMapper.toForexCandleResponses(candles);
    }
}
