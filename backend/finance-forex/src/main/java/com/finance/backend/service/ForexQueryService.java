package com.finance.backend.service;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.mapper.ForexResponseMapper;
import com.finance.backend.model.CandlePeriod;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.repository.ForexCandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class ForexQueryService {

    private final MarketCacheService<Forex, ForexCandle> forexCacheService;
    private final ForexCandleRepository forexCandleRepository;
    private final ForexResponseMapper forexResponseMapper;

    public List<CandleResponse> getForexHistory(String currencyCode, CandlePeriod period) {
        String normalized = currencyCode.strip().toUpperCase();
        if (period == CandlePeriod.ALL) {
            return forexResponseMapper.toForexCandleResponses(forexCacheService.getHistory(normalized));
        }
        LocalDateTime start = period.toStartDateTime();
        List<ForexCandle> candles = forexCandleRepository
                .findByCurrencyCodeAndCandleDateBetweenOrderByCandleDateAsc(normalized, start, LocalDateTime.now());
        return forexResponseMapper.toForexCandleResponses(candles);
    }
}
