package com.finance.market.stock.service;

import com.finance.common.config.AppProperties;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.dto.external.YahooCandleDto;
import com.finance.market.core.dto.internal.YahooChartFullResult;
import com.finance.market.stock.client.YahooStockClient;
import com.finance.market.stock.config.StockProperties;
import com.finance.market.stock.dto.external.YahooStockQuoteDto;
import com.finance.market.stock.model.Stock;
import com.finance.market.stock.repository.StockCandleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
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
class StockSnapshotProcessorTest {

    @Mock private YahooStockClient yahooStockClient;
    @Mock private StockCandleRepository stockCandleRepository;
    @SuppressWarnings("unchecked")
    @Mock private MarketCacheService<Stock> stockCacheService;
    @Mock private StockEntityWriter entityWriter;
    @Mock private TransactionTemplate transactionTemplate;

    private StockSnapshotProcessor processor;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        StockProperties stockProperties = new StockProperties();
        processor = new StockSnapshotProcessor(yahooStockClient, stockCandleRepository,
                stockCacheService, entityWriter, transactionTemplate, appProperties, stockProperties);
    }

    @SuppressWarnings("unchecked")
    private void stubTransactionTemplate() {
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
                inv.<TransactionCallback<Integer>>getArgument(0).doInTransaction(null));
    }

    private YahooStockQuoteDto quote(BigDecimal price) {
        return new YahooStockQuoteDto("AAPL", "Apple", price, price, BigDecimal.ZERO, BigDecimal.ZERO,
                price, price, price, 1000L, "NASDAQ", "USD");
    }

    private YahooCandleDto candle(int day, double close) {
        BigDecimal c = BigDecimal.valueOf(close);
        return new YahooCandleDto(LocalDateTime.of(2026, 5, day, 0, 0), c, c, c, c, 1000L);
    }

    private Stock stock(String symbol) {
        Stock s = new Stock();
        s.setSymbol(symbol);
        return s;
    }

    @Test
    void updateOne_persistsQuoteCandlesAndCaches_whenFreshDataAvailable() {
        Stock saved = stock("AAPL");
        YahooStockQuoteDto q = quote(new BigDecimal("150"));
        when(stockCandleRepository.findFirstByStockSymbolOrderByCandleDateDesc("AAPL"))
                .thenReturn(Optional.empty());
        when(yahooStockClient.fetchStockChartFull(eq("AAPL"), anyString(), anyString(), eq(true)))
                .thenReturn(new YahooChartFullResult<>(q, List.of(candle(12, 150))));
        stubTransactionTemplate();
        when(entityWriter.saveSnapshot(q, "AAPL")).thenReturn(saved);
        when(entityWriter.upsertCandles(eq("AAPL"), eq(saved), any())).thenReturn(1);

        int saved2 = processor.updateOne("AAPL");

        assertThat(saved2).isEqualTo(1);
        verify(entityWriter).refreshChangePercentFromCandles(saved);
        verify(stockCacheService).putSnapshot("AAPL", saved);
    }

    @Test
    void updateOne_skipsAll_whenQuoteAndCandlesEmpty() {
        when(stockCandleRepository.findFirstByStockSymbolOrderByCandleDateDesc("AAPL"))
                .thenReturn(Optional.empty());
        when(yahooStockClient.fetchStockChartFull(eq("AAPL"), anyString(), anyString(), eq(true)))
                .thenReturn(new YahooChartFullResult<>(null, List.of()));

        int saved = processor.updateOne("AAPL");

        assertThat(saved).isZero();
        verify(entityWriter, never()).saveSnapshot(any(), anyString());
        verify(stockCacheService, never()).putSnapshot(anyString(), any());
    }

    @Test
    void updateOne_returnsZero_whenNoFreshQuoteAndNoExistingSnapshot() {
        when(stockCandleRepository.findFirstByStockSymbolOrderByCandleDateDesc("AAPL"))
                .thenReturn(Optional.empty());
        when(yahooStockClient.fetchStockChartFull(eq("AAPL"), anyString(), anyString(), eq(true)))
                .thenReturn(new YahooChartFullResult<>(null, List.of(candle(12, 150))));
        stubTransactionTemplate();
        when(entityWriter.findExisting("AAPL")).thenReturn(null);

        int saved = processor.updateOne("AAPL");

        assertThat(saved).isZero();
        verify(entityWriter, never()).upsertCandles(anyString(), any(), any());
    }

    @Test
    void updateOne_usesExistingSnapshot_whenQuoteMissingButCandlesPresent() {
        Stock existing = stock("AAPL");
        when(stockCandleRepository.findFirstByStockSymbolOrderByCandleDateDesc("AAPL"))
                .thenReturn(Optional.empty());
        when(yahooStockClient.fetchStockChartFull(eq("AAPL"), anyString(), anyString(), eq(true)))
                .thenReturn(new YahooChartFullResult<>(null, List.of(candle(12, 150))));
        stubTransactionTemplate();
        when(entityWriter.findExisting("AAPL")).thenReturn(existing);
        when(entityWriter.upsertCandles(eq("AAPL"), eq(existing), any())).thenReturn(1);

        int saved = processor.updateOne("AAPL");

        assertThat(saved).isEqualTo(1);
        verify(entityWriter, never()).saveSnapshot(any(), anyString());
        verify(stockCacheService).putSnapshot("AAPL", existing);
    }

    @Test
    void updateOne_persistsQuoteOnly_whenCandlesEmpty() {
        Stock saved = stock("AAPL");
        YahooStockQuoteDto q = quote(new BigDecimal("150"));
        when(stockCandleRepository.findFirstByStockSymbolOrderByCandleDateDesc("AAPL"))
                .thenReturn(Optional.empty());
        when(yahooStockClient.fetchStockChartFull(eq("AAPL"), anyString(), anyString(), eq(true)))
                .thenReturn(new YahooChartFullResult<>(q, List.of()));
        stubTransactionTemplate();
        when(entityWriter.saveSnapshot(q, "AAPL")).thenReturn(saved);

        int result = processor.updateOne("AAPL");

        assertThat(result).isZero();
        verify(entityWriter, never()).upsertCandles(anyString(), any(), any());
        verify(stockCacheService).putSnapshot("AAPL", saved);
    }

    @Test
    void refreshOne_delegatesToUpdateOne_whenCodePresent() {
        Stock saved = stock("AAPL");
        YahooStockQuoteDto q = quote(new BigDecimal("150"));
        when(stockCandleRepository.findFirstByStockSymbolOrderByCandleDateDesc("AAPL"))
                .thenReturn(Optional.empty());
        when(yahooStockClient.fetchStockChartFull(eq("AAPL"), anyString(), anyString(), eq(true)))
                .thenReturn(new YahooChartFullResult<>(q, List.of(candle(12, 150))));
        stubTransactionTemplate();
        when(entityWriter.saveSnapshot(q, "AAPL")).thenReturn(saved);
        when(entityWriter.upsertCandles(eq("AAPL"), eq(saved), any())).thenReturn(1);

        processor.refreshOne("aapl");

        verify(stockCacheService).putSnapshot("AAPL", saved);
    }

    @Test
    void refreshOne_skipsRun_whenCodeIsBlank() {
        processor.refreshOne("   ");

        verify(yahooStockClient, never()).fetchStockChartFull(anyString(), anyString(), anyString(), any(Boolean.class));
    }

    @Test
    void exists_returnsTrue_whenQuoteHasCurrentPrice() {
        when(yahooStockClient.fetchStockChartFull(eq("AAPL"), eq("1d"), anyString(), eq(true)))
                .thenReturn(new YahooChartFullResult<>(quote(new BigDecimal("150")), List.of()));

        boolean result = processor.exists("aapl");

        assertThat(result).isTrue();
    }

    @Test
    void exists_returnsFalse_whenQuoteIsNull() {
        when(yahooStockClient.fetchStockChartFull(eq("AAPL"), eq("1d"), anyString(), eq(true)))
                .thenReturn(new YahooChartFullResult<>(null, List.of()));

        boolean result = processor.exists("AAPL");

        assertThat(result).isFalse();
    }

    @Test
    void exists_returnsFalse_whenLookupThrows() {
        when(yahooStockClient.fetchStockChartFull(eq("AAPL"), eq("1d"), anyString(), eq(true)))
                .thenThrow(new RuntimeException("Yahoo 503"));

        boolean result = processor.exists("AAPL");

        assertThat(result).isFalse();
    }

    @Test
    void exists_returnsFalse_whenSymbolIsBlank() {
        boolean result = processor.exists("   ");

        assertThat(result).isFalse();
    }
}
