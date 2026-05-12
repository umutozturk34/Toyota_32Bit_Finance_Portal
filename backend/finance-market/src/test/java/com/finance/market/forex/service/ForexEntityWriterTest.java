package com.finance.market.forex.service;

import com.finance.common.config.AppProperties;
import com.finance.common.model.Asset;
import com.finance.common.model.MarketType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.service.AssetRegistryService;
import com.finance.market.forex.config.ForexProperties;
import com.finance.market.forex.model.Forex;
import com.finance.market.forex.model.ForexCandle;
import com.finance.market.forex.repository.ForexCandleRepository;
import com.finance.market.forex.repository.ForexRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForexEntityWriterTest {

    @Mock private ForexRepository forexRepository;
    @Mock private ForexCandleRepository forexCandleRepository;
    @Mock private AssetRegistryService assetRegistry;
    @SuppressWarnings("unchecked")
    private final MarketCacheService<Forex> cacheService = org.mockito.Mockito.mock(MarketCacheService.class);
    private ForexProperties forexProperties;
    private ForexEntityWriter writer;
    private final ForexSerieMetadata usd =
            new ForexSerieMetadata("USD", "US Dollar", "ABD Doları", 1, true);

    @BeforeEach
    void setUp() {
        forexProperties = new ForexProperties();
        forexProperties.setFlagEmojis(Map.of("USD", "🇺🇸"));
        AppProperties appProperties = new AppProperties();
        writer = new ForexEntityWriter(forexRepository, forexCandleRepository, cacheService,
                assetRegistry, forexProperties, appProperties);
    }

    @Test
    void should_createAndSaveForexShell_when_repositoryHasNoMatch() {
        when(forexRepository.findById("USD")).thenReturn(Optional.empty());
        when(assetRegistry.upsert(MarketType.FOREX, "USD")).thenReturn(Asset.create(MarketType.FOREX, "USD"));
        when(forexRepository.save(any(Forex.class))).thenAnswer(inv -> inv.getArgument(0));

        Forex shell = writer.upsertForexShell(usd);

        ArgumentCaptor<Forex> captor = ArgumentCaptor.forClass(Forex.class);
        verify(forexRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrencyCode()).isEqualTo("USD");
        assertThat(shell.getImage()).isEqualTo("🇺🇸");
        assertThat(shell.getName()).isEqualTo("ABD Doları");
    }

    @Test
    void should_reuseExistingForex_when_repositoryReturnsMatch() {
        Forex existing = Forex.builder().currencyCode("USD").build();
        when(forexRepository.findById("USD")).thenReturn(Optional.of(existing));
        when(assetRegistry.upsert(MarketType.FOREX, "USD")).thenReturn(Asset.create(MarketType.FOREX, "USD"));
        when(forexRepository.save(existing)).thenReturn(existing);

        Forex shell = writer.upsertForexShell(usd);

        assertThat(shell).isSameAs(existing);
    }

    @Test
    void should_saveAndCacheSnapshot_when_saveSnapshotCalled() {
        Forex forex = Forex.builder().currencyCode("USD").sellingPrice(new BigDecimal("45.27")).build();
        when(forexRepository.save(forex)).thenReturn(forex);

        Forex result = writer.saveSnapshot(forex);

        assertThat(result).isSameAs(forex);
        verify(cacheService).putSnapshot(eq("USD"), eq(forex));
    }

    @Test
    void should_skipCandleSave_when_candidatesEmpty() {
        Forex forex = Forex.builder().currencyCode("USD").build();

        int saved = writer.upsertCandles(forex, List.of());

        assertThat(saved).isZero();
        verify(forexCandleRepository, never()).saveAll(any());
    }

    @Test
    void should_persistNewCandlesAndUpdateExisting_when_partialOverlap() {
        Forex forex = Forex.builder().currencyCode("USD").build();
        ForexCandle existingCandle = ForexCandle.builder()
                .currencyCode("USD")
                .candleDate(LocalDateTime.of(2026, 5, 10, 0, 0))
                .sellingPrice(new BigDecimal("45.22"))
                .build();
        ForexCandle candidateOld = ForexCandle.builder()
                .currencyCode("USD")
                .candleDate(LocalDateTime.of(2026, 5, 10, 0, 0))
                .sellingPrice(new BigDecimal("45.99"))
                .effectiveBuyingPrice(new BigDecimal("45.95"))
                .effectiveSellingPrice(new BigDecimal("46.05"))
                .build();
        ForexCandle candidateNew = ForexCandle.builder()
                .currencyCode("USD")
                .candleDate(LocalDateTime.of(2026, 5, 11, 0, 0))
                .sellingPrice(new BigDecimal("45.27"))
                .buyingPrice(new BigDecimal("45.19"))
                .effectiveBuyingPrice(new BigDecimal("45.15"))
                .effectiveSellingPrice(new BigDecimal("45.33"))
                .build();
        when(forexCandleRepository.findByCurrencyCodeAndCandleDateIn(eq("USD"), any()))
                .thenReturn(List.of(existingCandle));

        int touched = writer.upsertCandles(forex, List.of(candidateOld, candidateNew));

        ArgumentCaptor<List<ForexCandle>> captor = ArgumentCaptor.forClass(List.class);
        verify(forexCandleRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().getCandleDate()).isEqualTo(LocalDateTime.of(2026, 5, 11, 0, 0));
        assertThat(touched).isEqualTo(2);
        assertThat(existingCandle.getSellingPrice()).isEqualByComparingTo("45.99");
        assertThat(existingCandle.getEffectiveBuyingPrice()).isEqualByComparingTo("45.95");
    }
}
