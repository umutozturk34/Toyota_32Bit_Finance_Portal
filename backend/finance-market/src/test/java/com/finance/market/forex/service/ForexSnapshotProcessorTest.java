package com.finance.market.forex.service;

import com.finance.common.exception.ExternalApiException;
import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.forex.client.EvdsForexClient;
import com.finance.market.forex.mapper.ForexEvdsMapper;
import com.finance.market.forex.model.Forex;
import com.finance.market.forex.model.ForexCandle;
import com.finance.market.forex.repository.ForexCandleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForexSnapshotProcessorTest {

    @Mock private EvdsForexClient evdsClient;
    @Mock private EvdsForexCurrencyResolver currencyResolver;
    @Mock private ForexEvdsMapper evdsMapper;
    @Mock private ForexEntityWriter entityWriter;
    @Mock private ForexCandleRepository forexCandleRepository;
    @Mock private TransactionTemplate transactionTemplate;

    private ForexSnapshotProcessor processor;

    @BeforeEach
    void setUp() {
        com.finance.market.forex.config.ForexProperties forexProperties = new com.finance.market.forex.config.ForexProperties();
        processor = new ForexSnapshotProcessor(evdsClient, currencyResolver, evdsMapper,
                entityWriter, forexCandleRepository, transactionTemplate, forexProperties);
        lenient().when(entityWriter.getScale()).thenReturn(4);
    }

    private void stubTransactionTemplate() {
        doAnswer(inv -> {
            inv.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private ForexSerieMetadata meta(String code, boolean hasEfektif) {
        return new ForexSerieMetadata(code, "US Dollar", "ABD Doları", 1, hasEfektif);
    }

    private EvdsDataResponse response() {
        return new EvdsDataResponse(1, List.of());
    }

    private Forex forex(String code) {
        Forex f = new Forex();
        f.setCurrencyCode(code);
        return f;
    }

    private ForexCandle candle(String code, LocalDateTime date, BigDecimal sellingPrice) {
        ForexCandle c = new ForexCandle();
        c.setCurrencyCode(code);
        c.setCandleDate(date);
        c.setSellingPrice(sellingPrice);
        return c;
    }

    @Test
    void applyLatestSnapshot_skipsCandleUpsert_whenMapperReturnsEmpty() {
        ForexSerieMetadata m = meta("USD", true);
        Forex existing = forex("USD");
        when(entityWriter.upsertForexShell(m)).thenReturn(existing);
        when(evdsMapper.toCandles(eq(existing), eq(m), any(), eq(4))).thenReturn(List.of());
        when(evdsMapper.extractLatestRow(any(), eq(m))).thenReturn(null);
        when(entityWriter.saveSnapshot(existing)).thenReturn(existing);

        Forex result = processor.applyLatestSnapshot(m, response());

        assertThat(result).isSameAs(existing);
        verify(entityWriter, never()).upsertCandles(any(), any());
    }

    @Test
    void applyLatestSnapshot_appliesEvdsRow_andChangeFromCandles_whenLatestPresent() {
        ForexSerieMetadata m = meta("USD", true);
        Forex existing = forex("USD");
        existing.setSellingPrice(new BigDecimal("32.50"));
        ForexCandle today = candle("USD", LocalDateTime.of(2026, 5, 12, 0, 0), new BigDecimal("32.50"));
        ForexCandle yesterday = candle("USD", LocalDateTime.of(2026, 5, 11, 0, 0), new BigDecimal("32.40"));
        ForexEvdsMapper.ItemRow latest = new ForexEvdsMapper.ItemRow(
                LocalDateTime.of(2026, 5, 12, 0, 0),
                new BigDecimal("32.40"), new BigDecimal("32.50"),
                new BigDecimal("32.30"), new BigDecimal("32.60"));
        when(entityWriter.upsertForexShell(m)).thenReturn(existing);
        when(evdsMapper.toCandles(eq(existing), eq(m), any(), eq(4))).thenReturn(List.of(yesterday, today));
        when(evdsMapper.extractLatestRow(any(), eq(m))).thenReturn(latest);
        when(forexCandleRepository.findTop2ByCurrencyCodeOrderByCandleDateDesc("USD"))
                .thenReturn(List.of(today, yesterday));
        when(entityWriter.saveSnapshot(existing)).thenReturn(existing);

        Forex result = processor.applyLatestSnapshot(m, response());

        verify(entityWriter).upsertCandles(eq(existing), eq(List.of(yesterday, today)));
        verify(entityWriter).saveSnapshot(existing);
        assertThat(result).isSameAs(existing);
    }

    @Test
    void applyLatestSnapshot_skipsChange_whenSinglePriorCandle() {
        ForexSerieMetadata m = meta("USD", false);
        Forex existing = forex("USD");
        existing.setSellingPrice(new BigDecimal("32.50"));
        ForexCandle today = candle("USD", LocalDateTime.of(2026, 5, 12, 0, 0), new BigDecimal("32.50"));
        ForexEvdsMapper.ItemRow latest = new ForexEvdsMapper.ItemRow(
                LocalDateTime.of(2026, 5, 12, 0, 0),
                new BigDecimal("32.40"), new BigDecimal("32.50"), null, null);
        when(entityWriter.upsertForexShell(m)).thenReturn(existing);
        when(evdsMapper.toCandles(eq(existing), eq(m), any(), eq(4))).thenReturn(List.of(today));
        when(evdsMapper.extractLatestRow(any(), eq(m))).thenReturn(latest);
        when(forexCandleRepository.findTop2ByCurrencyCodeOrderByCandleDateDesc("USD"))
                .thenReturn(List.of(today));
        when(entityWriter.saveSnapshot(existing)).thenReturn(existing);

        processor.applyLatestSnapshot(m, response());

        verify(forexCandleRepository).findTop2ByCurrencyCodeOrderByCandleDateDesc("USD");
    }

    @Test
    void findLastCandleDate_returnsDate_whenCandleExists() {
        ForexCandle c = candle("USD", LocalDateTime.of(2026, 5, 12, 0, 0), new BigDecimal("32.50"));
        when(forexCandleRepository.findFirstByCurrencyCodeOrderByCandleDateDesc("USD"))
                .thenReturn(Optional.of(c));

        Optional<LocalDate> result = processor.findLastCandleDate("USD");

        assertThat(result).contains(LocalDate.of(2026, 5, 12));
    }

    @Test
    void findLastCandleDate_returnsEmpty_whenRepositoryEmpty() {
        when(forexCandleRepository.findFirstByCurrencyCodeOrderByCandleDateDesc("USD"))
                .thenReturn(Optional.empty());

        Optional<LocalDate> result = processor.findLastCandleDate("USD");

        assertThat(result).isEmpty();
    }

    @Test
    void refreshOne_appliesSnapshot_whenCurrencyMetaResolvesAndFetchSucceeds() {
        ForexSerieMetadata m = meta("USD", true);
        Forex saved = forex("USD");
        when(evdsClient.fetchDovizSerieList()).thenReturn(List.of());
        when(evdsClient.fetchEfektifSerieList()).thenReturn(List.of());
        when(currencyResolver.resolveActive(any(), any())).thenReturn(List.of(m));
        when(evdsClient.fetchForexData(any(), anyString(), anyString())).thenReturn(response());
        when(entityWriter.upsertForexShell(m)).thenReturn(saved);
        when(evdsMapper.toCandles(eq(saved), eq(m), any(), eq(4))).thenReturn(List.of());
        when(evdsMapper.extractLatestRow(any(), eq(m))).thenReturn(null);
        when(entityWriter.saveSnapshot(saved)).thenReturn(saved);
        stubTransactionTemplate();

        processor.refreshOne("USD");

        verify(entityWriter).saveSnapshot(saved);
    }

    @Test
    void refreshOne_skips_whenCurrencyMetaUnknown() {
        when(evdsClient.fetchDovizSerieList()).thenReturn(List.of());
        when(evdsClient.fetchEfektifSerieList()).thenReturn(List.of());
        when(currencyResolver.resolveActive(any(), any())).thenReturn(List.of(meta("EUR", true)));

        processor.refreshOne("USD");

        verify(entityWriter, never()).saveSnapshot(any());
    }

    @Test
    void refreshOne_skips_whenFetchThrowsExternalApiException() {
        ForexSerieMetadata m = meta("USD", false);
        when(evdsClient.fetchDovizSerieList()).thenReturn(List.of());
        when(evdsClient.fetchEfektifSerieList()).thenReturn(List.of());
        when(currencyResolver.resolveActive(any(), any())).thenReturn(List.of(m));
        when(evdsClient.fetchForexData(any(), anyString(), anyString()))
                .thenThrow(new ExternalApiException("EVDS", "down"));

        processor.refreshOne("USD");

        verify(entityWriter, never()).saveSnapshot(any());
    }

    @Test
    void refreshOne_skipsRun_whenCodeIsBlank() {
        processor.refreshOne("   ");

        verify(evdsClient, never()).fetchDovizSerieList();
    }

    @Test
    void exists_returnsTrue_whenResolverConfirmsActive() {
        when(evdsClient.fetchDovizSerieList()).thenReturn(List.of());
        when(currencyResolver.isActiveCurrencyCode(any(), eq("USD"))).thenReturn(true);

        boolean result = processor.exists("USD");

        assertThat(result).isTrue();
    }

    @Test
    void exists_returnsFalse_whenResolverDenies() {
        when(evdsClient.fetchDovizSerieList()).thenReturn(List.of());
        when(currencyResolver.isActiveCurrencyCode(any(), eq("ZZZ"))).thenReturn(false);

        boolean result = processor.exists("ZZZ");

        assertThat(result).isFalse();
    }

    @Test
    void exists_returnsFalse_whenLookupThrows() {
        when(evdsClient.fetchDovizSerieList()).thenThrow(new RuntimeException("EVDS 503"));

        boolean result = processor.exists("USD");

        assertThat(result).isFalse();
    }

    @Test
    void exists_returnsFalse_whenCodeIsBlank() {
        boolean result = processor.exists("   ");

        assertThat(result).isFalse();
    }
}
