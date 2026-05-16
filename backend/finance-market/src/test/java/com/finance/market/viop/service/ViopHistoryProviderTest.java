package com.finance.market.viop.service;

import com.finance.common.model.MarketType;
import com.finance.market.viop.dto.ViopHistoryPoint;
import com.finance.market.viop.model.ViopCandle;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopHistoryResolution;
import com.finance.market.viop.port.ViopMarketDataPort;
import com.finance.market.viop.repository.ViopCandleRepository;
import com.finance.market.viop.repository.ViopContractRepository;
import com.finance.shared.model.CandlePeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ViopHistoryProviderTest {

    @Mock private ViopMarketDataPort marketData;
    @Mock private ViopCandleRepository candleRepository;
    @Mock private ViopContractRepository contractRepository;

    private ViopHistoryProvider provider;
    private ViopContract contract;

    @BeforeEach
    void setUp() {
        provider = new ViopHistoryProvider(marketData, candleRepository, contractRepository);
        contract = ViopContract.builder().symbol("F_USDTRY0626").active(true).build();
    }

    @Test
    void should_returnViopMarketType_when_getMarketTypeCalled() {
        assertThat(provider.getMarketType()).isEqualTo(MarketType.VIOP);
    }

    @Test
    void should_returnZero_when_upsertTodayCandleCalledWithNullClose() {
        int written = provider.upsertTodayCandle("F_USDTRY0626", null);

        assertThat(written).isZero();
        verify(candleRepository, never()).save(any());
    }

    @Test
    void should_skipUpdate_when_existingCandleHasSameClose() {
        BigDecimal close = new BigDecimal("35.50");
        ViopCandle existing = ViopCandle.builder()
                .symbol("F_USDTRY0626")
                .candleDate(LocalDateTime.now())
                .close(close)
                .build();
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(
                eq("F_USDTRY0626"), any(), any())).thenReturn(List.of(existing));

        int written = provider.upsertTodayCandle("F_USDTRY0626", close);

        assertThat(written).isZero();
        verify(candleRepository, never()).save(any());
    }

    @Test
    void should_updateExistingCandle_when_closeDiffers() {
        ViopCandle existing = ViopCandle.builder()
                .symbol("F_USDTRY0626")
                .candleDate(LocalDateTime.now())
                .close(new BigDecimal("35.40"))
                .build();
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(
                eq("F_USDTRY0626"), any(), any())).thenReturn(List.of(existing));

        int written = provider.upsertTodayCandle("F_USDTRY0626", new BigDecimal("35.55"));

        assertThat(written).isEqualTo(1);
        verify(candleRepository).save(existing);
        assertThat(existing.getClose()).isEqualByComparingTo("35.55");
    }

    @Test
    void should_insertNewCandle_when_noneExistsForToday() {
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(
                anyString(), any(), any())).thenReturn(List.of());
        when(contractRepository.findBySymbol("F_USDTRY0626")).thenReturn(Optional.of(contract));

        int written = provider.upsertTodayCandle("F_USDTRY0626", new BigDecimal("35.60"));

        assertThat(written).isEqualTo(1);
        verify(candleRepository).save(any(ViopCandle.class));
    }

    @Test
    void should_skipInsert_when_contractMissingForUpsertTarget() {
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(
                anyString(), any(), any())).thenReturn(List.of());
        when(contractRepository.findBySymbol("F_UNKNOWN")).thenReturn(Optional.empty());

        int written = provider.upsertTodayCandle("F_UNKNOWN", new BigDecimal("100"));

        assertThat(written).isZero();
        verify(candleRepository, never()).save(any());
    }

    @Test
    void should_returnZero_when_fetchAndPersistGetsEmptyPointsFromUpstream() {
        when(marketData.fetchHistory(eq("F_USDTRY0626"), eq(ViopHistoryResolution.DAILY), any(), any()))
                .thenReturn(List.of());

        int persisted = provider.fetchAndPersist("F_USDTRY0626",
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1));

        assertThat(persisted).isZero();
        verify(candleRepository, never()).save(any());
    }

    @Test
    void should_swallowException_when_upstreamFetchThrows() {
        when(marketData.fetchHistory(anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("upstream down"));

        int persisted = provider.fetchAndPersist("F_USDTRY0626",
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1));

        assertThat(persisted).isZero();
    }

    @Test
    void should_skipFetch_when_storedCandleNewerThanRequestedTo() {
        ViopCandle latest = ViopCandle.builder()
                .symbol("F_USDTRY0626")
                .candleDate(LocalDateTime.of(2027, 1, 1, 12, 0))
                .close(new BigDecimal("40"))
                .build();
        when(candleRepository.findFirstBySymbolOrderByCandleDateDesc("F_USDTRY0626"))
                .thenReturn(Optional.of(latest));

        int persisted = provider.refreshCandlesUpTo("F_USDTRY0626", LocalDate.of(2026, 5, 1));

        assertThat(persisted).isZero();
        verify(marketData, never()).fetchHistory(anyString(), any(), any(), any());
    }

    @Test
    void should_returnHistoryInRangeFromCache_when_repositoryHasCandles() {
        ViopCandle c1 = ViopCandle.builder()
                .symbol("F_USDTRY0626")
                .candleDate(LocalDateTime.of(2026, 4, 1, 12, 0))
                .close(new BigDecimal("35.20")).build();
        when(candleRepository.findFirstBySymbolOrderByCandleDateDesc(anyString()))
                .thenReturn(Optional.of(c1));
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(
                anyString(), any(), any())).thenReturn(List.of(c1));

        List<ViopHistoryPoint> points = provider.getHistoryInRange(
                "F_USDTRY0626", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 1));

        assertThat(points).hasSize(1);
        assertThat(points.get(0).close()).isEqualByComparingTo("35.20");
    }

    @Test
    void should_clampHistoryWindowToFiveYears_when_periodExceedsLimit() {
        when(candleRepository.findFirstBySymbolOrderByCandleDateDesc(anyString()))
                .thenReturn(Optional.empty());
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(
                anyString(), any(), any())).thenReturn(List.of());
        when(marketData.fetchHistory(anyString(), any(), any(), any())).thenReturn(List.of());

        provider.getHistory("F_USDTRY0626", CandlePeriod.ALL);

        verify(candleRepository).findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(
                eq("F_USDTRY0626"), any(), any());
    }
}
