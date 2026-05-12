package com.finance.market.crypto.service;

import com.finance.common.config.AppProperties;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.crypto.client.CoinGeckoClient;
import com.finance.market.crypto.config.CryptoProperties;
import com.finance.market.crypto.dto.external.CoinGeckoCandleDto;
import com.finance.market.crypto.mapper.CryptoMapper;
import com.finance.market.crypto.model.Crypto;
import com.finance.market.crypto.model.CryptoCandle;
import com.finance.market.crypto.repository.CryptoCandleRepository;
import com.finance.market.crypto.repository.CryptoRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoUpdateServiceTest {

    @Mock private CoinGeckoClient coinGeckoClient;
    @Mock private CryptoMapper cryptoMapper;
    @Mock private CryptoRepository cryptoRepository;
    @Mock private CryptoCandleRepository cryptoCandleRepository;
    @Mock private CryptoSnapshotProcessor snapshotProcessor;
    @Mock private CryptoEntityWriter entityWriter;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;
    @Mock private CryptoSymbolResolver cryptoSymbolResolver;
    @Mock private TransactionTemplate transactionTemplate;

    private CryptoUpdateService service;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        CryptoProperties cryptoProperties = new CryptoProperties();
        cryptoProperties.setBinancePageSize(2);
        cryptoProperties.setMinCandlesForHealthy(5);
        cryptoProperties.setHistoryDays(7);
        service = new CryptoUpdateService(coinGeckoClient, cryptoMapper, cryptoRepository,
                cryptoCandleRepository, snapshotProcessor, entityWriter, trackedAssetQueryService,
                cryptoSymbolResolver, transactionTemplate, appProperties, cryptoProperties);
    }

    private void stubTransactionTemplate() {
        doAnswer(inv -> {
            inv.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private CoinGeckoCandleDto candleDto(String coinId, LocalDateTime date) {
        return new CoinGeckoCandleDto(coinId, date,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1L);
    }

    private CryptoCandle entityCandle(String coinId, LocalDateTime date) {
        CryptoCandle c = new CryptoCandle();
        c.setCryptoId(coinId);
        c.setCandleDate(date);
        return c;
    }

    @Test
    void getMarketType_returnsCrypto() {
        assertThat(service.getMarketType()).isEqualTo(MarketType.CRYPTO);
    }

    @Test
    void refreshAll_delegatesToSnapshotAndCandleRefresh() {
        when(trackedAssetQueryService.getCodes(TrackedAssetType.CRYPTO)).thenReturn(List.of());

        service.refreshAll();

        verify(snapshotProcessor).refreshAll();
    }

    @Test
    void refresh_delegatesToSnapshotAndCandlesForCoin() {
        when(cryptoSymbolResolver.resolveBinanceSymbol("bitcoin")).thenReturn(null);

        service.refresh("bitcoin");

        verify(snapshotProcessor).refreshOne("bitcoin");
    }

    @Test
    void refresh_skipsCandleUpdate_whenCodeIsBlank() {
        service.refresh("   ");

        verify(snapshotProcessor).refreshOne("   ");
        verify(cryptoSymbolResolver, never()).resolveBinanceSymbol(anyString());
    }

    @Test
    void exists_delegatesToSnapshotProcessor() {
        when(snapshotProcessor.exists("bitcoin")).thenReturn(true);

        assertThat(service.exists("bitcoin")).isTrue();
    }

    @Test
    void existsWithBinanceSymbol_delegatesToSnapshotProcessor() {
        when(snapshotProcessor.exists("bitcoin", "BTCUSDT")).thenReturn(true);

        assertThat(service.exists("bitcoin", "BTCUSDT")).isTrue();
    }

    @Test
    void updateCandlesForCoin_skipsRefresh_whenBinanceMappingMissing() {
        when(cryptoSymbolResolver.resolveBinanceSymbol("dogecoin")).thenReturn(null);

        service.refresh("dogecoin");

        verify(coinGeckoClient, never()).fetchBinanceKlines(anyString(), anyString(), anyLong(), anyInt());
    }

    @Test
    void updateCandlesForCoin_triggersFullReload_whenCandleCountBelowThreshold() {
        Crypto crypto = new Crypto();
        crypto.setId("bitcoin");
        when(cryptoSymbolResolver.resolveBinanceSymbol("bitcoin")).thenReturn("BTCUSDT");
        when(cryptoCandleRepository.countByCryptoId("bitcoin")).thenReturn(2L);
        when(coinGeckoClient.fetchBinanceKlines(eq("bitcoin"), eq("BTCUSDT"), eq(0L), eq(2)))
                .thenReturn(List.of(candleDto("bitcoin", LocalDateTime.of(2026, 5, 10, 0, 0))));
        when(cryptoRepository.getReferenceById("bitcoin")).thenReturn(crypto);
        when(cryptoMapper.toCandleEntity(any(), eq(crypto)))
                .thenReturn(entityCandle("bitcoin", LocalDateTime.of(2026, 5, 10, 0, 0)));
        stubTransactionTemplate();

        service.refresh("bitcoin");

        verify(entityWriter).replaceCandleHistory(eq("bitcoin"), any());
    }

    @Test
    void updateCandlesForCoin_paginatesUntilSmallerPage() {
        Crypto crypto = new Crypto();
        crypto.setId("bitcoin");
        when(cryptoSymbolResolver.resolveBinanceSymbol("bitcoin")).thenReturn("BTCUSDT");
        when(cryptoCandleRepository.countByCryptoId("bitcoin")).thenReturn(1L);
        when(coinGeckoClient.fetchBinanceKlines(eq("bitcoin"), eq("BTCUSDT"), anyLong(), eq(2)))
                .thenReturn(List.of(
                        candleDto("bitcoin", LocalDateTime.of(2026, 5, 1, 0, 0)),
                        candleDto("bitcoin", LocalDateTime.of(2026, 5, 2, 0, 0))))
                .thenReturn(List.of(candleDto("bitcoin", LocalDateTime.of(2026, 5, 3, 0, 0))));
        when(cryptoRepository.getReferenceById("bitcoin")).thenReturn(crypto);
        when(cryptoMapper.toCandleEntity(any(), eq(crypto)))
                .thenReturn(entityCandle("bitcoin", LocalDateTime.of(2026, 5, 1, 0, 0)));
        stubTransactionTemplate();

        service.refresh("bitcoin");

        verify(coinGeckoClient, times(2)).fetchBinanceKlines(eq("bitcoin"), eq("BTCUSDT"), anyLong(), eq(2));
        verify(entityWriter).replaceCandleHistory(eq("bitcoin"), any());
    }

    @Test
    void updateCandlesForCoin_skipsReplace_whenNoCandlesReturned() {
        when(cryptoSymbolResolver.resolveBinanceSymbol("bitcoin")).thenReturn("BTCUSDT");
        when(cryptoCandleRepository.countByCryptoId("bitcoin")).thenReturn(1L);
        when(coinGeckoClient.fetchBinanceKlines(eq("bitcoin"), eq("BTCUSDT"), eq(0L), eq(2)))
                .thenReturn(List.of());

        service.refresh("bitcoin");

        verify(entityWriter, never()).replaceCandleHistory(anyString(), any());
    }

    @Test
    void updateCandlesForCoin_triggersFullReload_whenGapDaysExceedHistoryDays() {
        Crypto crypto = new Crypto();
        crypto.setId("bitcoin");
        when(cryptoSymbolResolver.resolveBinanceSymbol("bitcoin")).thenReturn("BTCUSDT");
        when(cryptoCandleRepository.countByCryptoId("bitcoin")).thenReturn(100L);
        when(cryptoCandleRepository.findFirstByCryptoIdOrderByCandleDateDesc("bitcoin"))
                .thenReturn(Optional.of(entityCandle("bitcoin",
                        LocalDate.now().minusDays(30).atStartOfDay())));
        when(coinGeckoClient.fetchBinanceKlines(eq("bitcoin"), eq("BTCUSDT"), eq(0L), eq(2)))
                .thenReturn(List.of(candleDto("bitcoin", LocalDateTime.of(2026, 5, 10, 0, 0))));
        when(cryptoRepository.getReferenceById("bitcoin")).thenReturn(crypto);
        when(cryptoMapper.toCandleEntity(any(), eq(crypto)))
                .thenReturn(entityCandle("bitcoin", LocalDateTime.of(2026, 5, 10, 0, 0)));
        stubTransactionTemplate();

        service.refresh("bitcoin");

        verify(entityWriter).replaceCandleHistory(eq("bitcoin"), any());
    }

    @Test
    void updateCandlesForCoin_appendsSinceLastCandle_whenGapWithinHistoryWindow() {
        Crypto crypto = new Crypto();
        crypto.setId("bitcoin");
        when(cryptoSymbolResolver.resolveBinanceSymbol("bitcoin")).thenReturn("BTCUSDT");
        when(cryptoCandleRepository.countByCryptoId("bitcoin")).thenReturn(100L);
        when(cryptoCandleRepository.findFirstByCryptoIdOrderByCandleDateDesc("bitcoin"))
                .thenReturn(Optional.of(entityCandle("bitcoin",
                        LocalDate.now().minusDays(2).atStartOfDay())));
        when(coinGeckoClient.fetchBinanceKlines(eq("bitcoin"), eq("BTCUSDT"), anyLong(), anyInt()))
                .thenReturn(List.of(candleDto("bitcoin", LocalDateTime.now())));
        when(cryptoRepository.getReferenceById("bitcoin")).thenReturn(crypto);
        when(entityWriter.upsertCandles(eq("bitcoin"), eq(crypto), any())).thenReturn(1);
        stubTransactionTemplate();

        service.refresh("bitcoin");

        verify(entityWriter).upsertCandles(eq("bitcoin"), eq(crypto), any());
        verify(entityWriter, never()).replaceCandleHistory(anyString(), any());
    }

    @Test
    void fetchAndSaveSinceLastCandle_skipsWrite_whenNoNewCandles() {
        when(cryptoSymbolResolver.resolveBinanceSymbol("bitcoin")).thenReturn("BTCUSDT");
        when(cryptoCandleRepository.countByCryptoId("bitcoin")).thenReturn(100L);
        when(cryptoCandleRepository.findFirstByCryptoIdOrderByCandleDateDesc("bitcoin"))
                .thenReturn(Optional.of(entityCandle("bitcoin",
                        LocalDate.now().minusDays(2).atStartOfDay())));
        when(coinGeckoClient.fetchBinanceKlines(eq("bitcoin"), eq("BTCUSDT"), anyLong(), anyInt()))
                .thenReturn(List.of());

        service.refresh("bitcoin");

        verify(entityWriter, never()).upsertCandles(anyString(), any(), any());
        verify(entityWriter, never()).replaceCandleHistory(anyString(), any());
    }

    @Test
    void refreshAll_includesCandleRefreshForTrackedCoins() {
        when(trackedAssetQueryService.getCodes(TrackedAssetType.CRYPTO)).thenReturn(List.of("bitcoin"));
        when(cryptoSymbolResolver.resolveBinanceSymbol("bitcoin")).thenReturn(null);

        service.refreshAll();

        verify(cryptoSymbolResolver).resolveBinanceSymbol("bitcoin");
    }
}
