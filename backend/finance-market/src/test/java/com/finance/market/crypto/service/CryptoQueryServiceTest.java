package com.finance.market.crypto.service;

import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.dto.response.CandleResponse;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.crypto.mapper.CryptoResponseMapper;
import com.finance.market.crypto.model.CryptoCandle;
import com.finance.market.crypto.repository.CryptoCandleRepository;
import com.finance.shared.model.CandlePeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoQueryServiceTest {

    @Mock private CryptoCandleRepository cryptoCandleRepository;
    @Mock private CryptoResponseMapper cryptoResponseMapper;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;

    private CryptoQueryService service;

    @BeforeEach
    void setUp() {
        service = new CryptoQueryService(cryptoCandleRepository, cryptoResponseMapper, trackedAssetQueryService);
    }

    @Test
    void getMarketType_returnsCrypto() {
        assertThat(service.getMarketType()).isEqualTo(MarketType.CRYPTO);
    }

    @Test
    void getHistory_resolvesCode_andDelegatesToRepository() {
        List<CryptoCandle> candles = List.of(new CryptoCandle());
        List<CandleResponse> mapped = List.of();
        when(trackedAssetQueryService.resolveCodeOrThrow(TrackedAssetType.CRYPTO, "btc"))
                .thenReturn("bitcoin");
        when(cryptoCandleRepository.findByCryptoIdAndCandleDateBetweenOrderByCandleDateAsc(
                eq("bitcoin"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(candles);
        when(cryptoResponseMapper.toCryptoCandleResponses(candles)).thenReturn(mapped);

        List<CandleResponse> result = service.getHistory("btc", CandlePeriod.ONE_MONTH);

        assertThat(result).isSameAs(mapped);
        verify(cryptoResponseMapper).toCryptoCandleResponses(candles);
    }

    @Test
    void getHistoryInRange_passesStartAndEndOfDay() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 5);
        when(trackedAssetQueryService.resolveCodeOrThrow(TrackedAssetType.CRYPTO, "eth"))
                .thenReturn("ethereum");
        when(cryptoCandleRepository.findByCryptoIdAndCandleDateBetweenOrderByCandleDateAsc(
                eq("ethereum"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(cryptoResponseMapper.toCryptoCandleResponses(List.of())).thenReturn(List.of());

        service.getHistoryInRange("eth", from, to);

        ArgumentCaptor<LocalDateTime> fromCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(cryptoCandleRepository).findByCryptoIdAndCandleDateBetweenOrderByCandleDateAsc(
                eq("ethereum"), fromCap.capture(), toCap.capture());
        assertThat(fromCap.getValue()).isEqualTo(from.atStartOfDay());
        assertThat(toCap.getValue()).isEqualTo(to.atTime(LocalTime.MAX));
    }
}
