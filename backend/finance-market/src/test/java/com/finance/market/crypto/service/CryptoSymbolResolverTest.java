package com.finance.market.crypto.service;

import com.finance.market.core.service.TrackedAssetQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoSymbolResolverTest {

    @Mock private TrackedAssetQueryService trackedAssetQueryService;

    private CryptoSymbolResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CryptoSymbolResolver(trackedAssetQueryService);
    }

    @Test
    void should_returnMappedSymbol_when_coinIdIsTracked() {
        when(trackedAssetQueryService.getCryptoBinanceSymbol("bitcoin")).thenReturn(Optional.of("BTCUSDT"));

        String symbol = resolver.resolveBinanceSymbol("bitcoin");

        assertThat(symbol).isEqualTo("BTCUSDT");
    }

    @Test
    void should_returnNull_when_coinIdIsUnmapped() {
        when(trackedAssetQueryService.getCryptoBinanceSymbol("dogecoin")).thenReturn(Optional.empty());

        String symbol = resolver.resolveBinanceSymbol("dogecoin");

        assertThat(symbol).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void should_returnNullWithoutLookup_when_coinIdIsBlank(String blankId) {
        String symbol = resolver.resolveBinanceSymbol(blankId);

        assertThat(symbol).isNull();
        verify(trackedAssetQueryService, never()).getCryptoBinanceSymbol(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void should_returnNullWithoutLookup_when_coinIdIsNull() {
        String symbol = resolver.resolveBinanceSymbol(null);

        assertThat(symbol).isNull();
        verify(trackedAssetQueryService, never()).getCryptoBinanceSymbol(org.mockito.ArgumentMatchers.any());
    }
}
