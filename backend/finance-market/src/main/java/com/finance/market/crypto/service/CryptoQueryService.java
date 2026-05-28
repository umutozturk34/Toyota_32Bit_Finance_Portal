package com.finance.market.crypto.service;
import com.finance.market.core.service.MarketHistoryProvider;

import com.finance.market.core.service.TrackedAssetQueryService;


import com.finance.market.core.dto.response.CandleResponse;
import com.finance.market.crypto.mapper.CryptoResponseMapper;
import com.finance.shared.model.CandlePeriod;
import com.finance.market.crypto.model.CryptoCandle;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.crypto.repository.CryptoCandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Serves crypto candle history for a tracked coin over a preset period or explicit range. */
@Log4j2
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CryptoQueryService implements MarketHistoryProvider {

    private final CryptoCandleRepository cryptoCandleRepository;
    private final CryptoResponseMapper cryptoResponseMapper;
    private final TrackedAssetQueryService trackedAssetQueryService;

    @Override
    public MarketType getMarketType() {
        return MarketType.CRYPTO;
    }

    @Override
    public List<CandleResponse> getHistory(String id, CandlePeriod period) {
        return loadCandles(id, period.toStartDateTime(), LocalDateTime.now());
    }

    @Override
    public List<CandleResponse> getHistoryInRange(String id, LocalDate from, LocalDate to) {
        return loadCandles(id, from.atStartOfDay(), to.atTime(LocalTime.MAX));
    }

    private List<CandleResponse> loadCandles(String id, LocalDateTime from, LocalDateTime to) {
        String normalizedCode = trackedAssetQueryService.resolveCodeOrThrow(TrackedAssetType.CRYPTO, id);
        List<CryptoCandle> candles = cryptoCandleRepository
                .findByCryptoIdAndCandleDateBetweenOrderByCandleDateAsc(normalizedCode, from, to);
        return cryptoResponseMapper.toCryptoCandleResponses(candles);
    }
}
