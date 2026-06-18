package com.finance.market.crypto.service;

import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.crypto.client.CoinGeckoClient;
import com.finance.market.crypto.config.CryptoProperties;
import com.finance.market.crypto.dto.external.CoinGeckoCandleDto;
import com.finance.market.crypto.dto.external.CoinGeckoSnapshotDto;
import com.finance.market.crypto.model.Crypto;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoSnapshotProcessorTest {

    @Mock private CoinGeckoClient coinGeckoClient;
    @Mock private CryptoEntityWriter entityWriter;
    @SuppressWarnings("unchecked")
    @Mock private MarketCacheService<Crypto> cryptoCacheService;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;
    @Mock private TransactionTemplate transactionTemplate;

    private CryptoSnapshotProcessor processor;

    @BeforeEach
    void setUp() {
        CryptoProperties cryptoProperties = new CryptoProperties();
        processor = new CryptoSnapshotProcessor(coinGeckoClient, entityWriter, cryptoCacheService,
                trackedAssetQueryService, transactionTemplate, cryptoProperties);
    }

    private void stubTransactionTemplate() {
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
                inv.<TransactionCallback<Crypto>>getArgument(0).doInTransaction(null));
    }

    private CoinGeckoSnapshotDto usdDto(String id, double price) {
        return new CoinGeckoSnapshotDto(id, id.toUpperCase(), id + "-name", "img",
                BigDecimal.valueOf(price), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private Crypto crypto(String id) {
        Crypto c = new Crypto();
        c.setId(id);
        return c;
    }

    private CoinGeckoCandleDto candle(String coinId, double close) {
        BigDecimal v = BigDecimal.valueOf(close);
        return new CoinGeckoCandleDto(coinId, LocalDateTime.of(2026, 5, 12, 0, 0), v, v, v, v, 1L);
    }

    @Test
    void refreshAll_persistsAndCachesEachUsdMarket_withMatchingTryPrice() {
        when(trackedAssetQueryService.getCodes(TrackedAssetType.CRYPTO))
                .thenReturn(List.of("bitcoin", "ethereum"));
        when(coinGeckoClient.fetchMarkets(eq("usd"), any()))
                .thenReturn(List.of(usdDto("bitcoin", 60000), usdDto("ethereum", 3000)));
        when(coinGeckoClient.fetchMarkets(eq("try"), any()))
                .thenReturn(List.of(usdDto("bitcoin", 1950000), usdDto("ethereum", 97500)));
        Crypto btc = crypto("bitcoin");
        Crypto eth = crypto("ethereum");
        stubTransactionTemplate();
        when(entityWriter.saveSnapshot(any(CoinGeckoSnapshotDto.class), eq(new BigDecimal("1950000.0"))))
                .thenReturn(btc);
        when(entityWriter.saveSnapshot(any(CoinGeckoSnapshotDto.class), eq(new BigDecimal("97500.0"))))
                .thenReturn(eth);

        processor.refreshAll();

        verify(cryptoCacheService).putSnapshot("bitcoin", btc);
        verify(cryptoCacheService).putSnapshot("ethereum", eth);
    }

    @Test
    void refreshAll_passesNullTryPrice_whenTryMarketsMissingCoin() {
        when(trackedAssetQueryService.getCodes(TrackedAssetType.CRYPTO))
                .thenReturn(List.of("bitcoin"));
        when(coinGeckoClient.fetchMarkets(eq("usd"), any()))
                .thenReturn(List.of(usdDto("bitcoin", 60000)));
        when(coinGeckoClient.fetchMarkets(eq("try"), any())).thenReturn(List.of());
        Crypto btc = crypto("bitcoin");
        stubTransactionTemplate();
        when(entityWriter.saveSnapshot(any(CoinGeckoSnapshotDto.class), eq((BigDecimal) null)))
                .thenReturn(btc);

        processor.refreshAll();

        verify(cryptoCacheService).putSnapshot("bitcoin", btc);
    }

    @Test
    void refreshAll_skipsBatchRun_whenUsdMarketsEmpty() {
        when(trackedAssetQueryService.getCodes(TrackedAssetType.CRYPTO))
                .thenReturn(List.of());
        when(coinGeckoClient.fetchMarkets(eq("usd"), any())).thenReturn(List.of());
        when(coinGeckoClient.fetchMarkets(eq("try"), any())).thenReturn(List.of());

        processor.refreshAll();

        verify(entityWriter, never()).saveSnapshot(any(), any());
        verify(cryptoCacheService, never()).putSnapshot(any(), any());
    }

    @Test
    void refreshOne_persistsAndCaches_whenUsdMarketExists() {
        CoinGeckoSnapshotDto usd = usdDto("bitcoin", 60000);
        CoinGeckoSnapshotDto tryDto = usdDto("bitcoin", 1950000);
        Crypto saved = crypto("bitcoin");
        when(coinGeckoClient.fetchMarkets(eq("usd"), eq(List.of("bitcoin")))).thenReturn(List.of(usd));
        when(coinGeckoClient.fetchMarkets(eq("try"), eq(List.of("bitcoin")))).thenReturn(List.of(tryDto));
        stubTransactionTemplate();
        when(entityWriter.saveSnapshot(usd, BigDecimal.valueOf(1950000.0))).thenReturn(saved);

        processor.refreshOne("bitcoin");

        verify(cryptoCacheService).putSnapshot("bitcoin", saved);
    }

    @Test
    void refreshOne_skipsSave_whenUsdMarketEmpty() {
        when(coinGeckoClient.fetchMarkets(eq("usd"), eq(List.of("bitcoin")))).thenReturn(List.of());

        processor.refreshOne("bitcoin");

        verify(entityWriter, never()).saveSnapshot(any(), any());
        verify(cryptoCacheService, never()).putSnapshot(any(), any());
    }

    @Test
    void refreshOne_passesNullTryPrice_whenTryMarketEmpty() {
        CoinGeckoSnapshotDto usd = usdDto("bitcoin", 60000);
        Crypto saved = crypto("bitcoin");
        when(coinGeckoClient.fetchMarkets(eq("usd"), eq(List.of("bitcoin")))).thenReturn(List.of(usd));
        when(coinGeckoClient.fetchMarkets(eq("try"), eq(List.of("bitcoin")))).thenReturn(List.of());
        stubTransactionTemplate();
        when(entityWriter.saveSnapshot(usd, null)).thenReturn(saved);

        processor.refreshOne("bitcoin");

        verify(cryptoCacheService).putSnapshot("bitcoin", saved);
    }

    @Test
    void refreshOne_skipsRun_whenCodeIsBlank() {
        processor.refreshOne("   ");

        verify(coinGeckoClient, never()).fetchMarkets(any(), any());
    }

    @Test
    void exists_returnsFalse_whenCoinGeckoFetchEmpty() {
        when(coinGeckoClient.fetchMarkets(eq("usd"), eq(List.of("bitcoin")))).thenReturn(List.of());

        boolean result = processor.exists("bitcoin");

        assertThat(result).isFalse();
    }

    @Test
    void exists_returnsTrue_whenCoinGeckoHasMatch_andNoBinanceSymbolProvided() {
        when(coinGeckoClient.fetchMarkets(eq("usd"), eq(List.of("bitcoin"))))
                .thenReturn(List.of(usdDto("bitcoin", 60000)));

        boolean result = processor.exists("bitcoin");

        assertThat(result).isTrue();
    }

    @Test
    void exists_withBinanceSymbol_returnsTrue_whenKlinesNonEmpty() {
        when(coinGeckoClient.fetchMarkets(eq("usd"), eq(List.of("bitcoin"))))
                .thenReturn(List.of(usdDto("bitcoin", 60000)));
        when(coinGeckoClient.fetchBinanceKlines(eq("bitcoin"), eq("BTCUSDT"), anyLong(), eq(1)))
                .thenReturn(List.of(candle("bitcoin", 60000)));

        boolean result = processor.exists("bitcoin", "btcusdt");

        assertThat(result).isTrue();
    }

    @Test
    void exists_withBinanceSymbol_returnsFalse_whenKlinesEmpty() {
        when(coinGeckoClient.fetchMarkets(eq("usd"), eq(List.of("bitcoin"))))
                .thenReturn(List.of(usdDto("bitcoin", 60000)));
        when(coinGeckoClient.fetchBinanceKlines(eq("bitcoin"), eq("BTCUSDT"), anyLong(), eq(1)))
                .thenReturn(List.of());

        boolean result = processor.exists("bitcoin", "btcusdt");

        assertThat(result).isFalse();
    }

    @Test
    void exists_withBlankBinanceSymbol_returnsTrue_whenCoinGeckoMatches() {
        when(coinGeckoClient.fetchMarkets(eq("usd"), eq(List.of("bitcoin"))))
                .thenReturn(List.of(usdDto("bitcoin", 60000)));

        boolean result = processor.exists("bitcoin", "   ");

        assertThat(result).isTrue();
    }

    @Test
    void exists_propagatesTemporarilyUnavailable_whenCoinGeckoLookupFailsTransiently() {
        when(coinGeckoClient.fetchMarkets(eq("usd"), eq(List.of("bitcoin"))))
                .thenThrow(new RuntimeException("CoinGecko 429"));

        // A transient upstream failure (e.g. 429 rate-limit) must NOT be reported as "does not exist".
        assertThatThrownBy(() -> processor.exists("bitcoin"))
                .isInstanceOf(com.finance.common.exception.BusinessException.class)
                .hasMessage("error.market.dataTemporarilyUnavailable");
    }

    @Test
    void exists_returnsFalse_whenCoinIdIsBlank() {
        boolean result = processor.exists("   ");

        assertThat(result).isFalse();
    }
}
