package com.finance.backend.service;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.mapper.CryptoResponseMapper;
import com.finance.backend.model.CandlePeriod;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.CryptoCandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
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
        String normalizedCode = trackedAssetQueryService.resolveEnabledCodeOrThrow(TrackedAssetType.CRYPTO, id);
        List<CryptoCandle> candles = cryptoCandleRepository
                .findByCryptoIdAndCandleDateBetweenOrderByCandleDateAsc(
                        normalizedCode, period.toStartDateTime(), LocalDateTime.now());
        return cryptoResponseMapper.toCryptoCandleResponses(candles);
    }
}
