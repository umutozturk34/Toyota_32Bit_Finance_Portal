package com.finance.market.fund.service;

import com.finance.common.config.AppProperties;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.fund.client.TefasClient;
import com.finance.market.fund.config.FundProperties;
import com.finance.market.fund.dto.external.TefasFundDto;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
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

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundSnapshotProcessorTest {

    @Mock private TefasClient tefasClient;
    @Mock private FundEntityWriter entityWriter;
    @SuppressWarnings("unchecked")
    @Mock private MarketCacheService<Fund> fundCacheService;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;
    @Mock private TransactionTemplate transactionTemplate;

    private FundSnapshotProcessor processor;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        FundProperties fundProperties = new FundProperties();
        processor = new FundSnapshotProcessor(tefasClient, entityWriter, fundCacheService,
                trackedAssetQueryService, transactionTemplate, appProperties, fundProperties);
    }

    private void stubTransactionTemplate() {
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
                inv.<TransactionCallback<Fund>>getArgument(0).doInTransaction(null));
    }

    private TefasFundDto dto(String code) {
        return new TefasFundDto(code, "name " + code,
                LocalDateTime.of(2026, 5, 12, 0, 0),
                new BigDecimal("1.00"), null, null, null, null);
    }

    private Fund fund(String code) {
        Fund f = new Fund();
        f.setFundCode(code);
        f.setName("name " + code);
        return f;
    }

    @Test
    void refreshAll_persistsAndCachesByf_whenBulkFetchReturnsData() {
        TefasFundDto byfDto = dto("BTC1");
        Fund savedByf = fund("BTC1");
        when(tefasClient.bulkFetch(eq(FundType.BYF), any(), any())).thenReturn(List.of(byfDto));
        when(tefasClient.bulkFetch(eq(FundType.YAT), any(), any())).thenReturn(List.of());
        stubTransactionTemplate();
        when(entityWriter.saveSnapshot(byfDto, FundType.BYF)).thenReturn(savedByf);

        processor.refreshAll();

        verify(entityWriter).saveSnapshot(byfDto, FundType.BYF);
        verify(entityWriter).upsertCandleFromDto(savedByf, FundType.BYF, byfDto);
        verify(entityWriter).ensureByfTracked("BTC1", "name BTC1");
        verify(fundCacheService).putSnapshot("BTC1", savedByf);
    }

    @Test
    void refreshAll_usesMostRecentUsableRow_whenLatestDayNavMissing() {
        // Window holds two rows for one YAT fund: the latest day has no NAV, the prior day does. The snapshot must
        // persist the prior day's valid NAV (its most recent usable price) instead of dropping the fund.
        TefasFundDto older = new TefasFundDto("YF1", "name YF1",
                LocalDateTime.of(2026, 5, 11, 0, 0), new BigDecimal("5.00"), null, null, null, null);
        TefasFundDto newerNull = new TefasFundDto("YF1", "name YF1",
                LocalDateTime.of(2026, 5, 12, 0, 0), null, null, null, null, null);
        when(tefasClient.bulkFetch(eq(FundType.YAT), any(), any())).thenReturn(List.of(older, newerNull));
        when(tefasClient.bulkFetch(eq(FundType.BYF), any(), any())).thenReturn(List.of());
        stubTransactionTemplate();
        when(entityWriter.saveSnapshot(any(), eq(FundType.YAT))).thenReturn(fund("YF1"));

        processor.refreshAll();

        ArgumentCaptor<TefasFundDto> captor = ArgumentCaptor.forClass(TefasFundDto.class);
        verify(entityWriter).saveSnapshot(captor.capture(), eq(FundType.YAT));
        assertThat(captor.getValue().price()).isEqualByComparingTo("5.00");
        assertThat(captor.getValue().date()).isEqualTo(LocalDateTime.of(2026, 5, 11, 0, 0));
    }

    @Test
    void refreshAll_tracksByf_whenNavMissingButBulletinPresent_withoutFakingNav() {
        // An ETF whose NAV (fiyat) wasn't published that day but has a valid exchange bulletin price must still be
        // TRACKED so it shows in the list (the cause of "only 10 of 30" was dropping these) — WITHOUT faking the
        // NAV: price stays null, bulletin is preserved, and the next daily snapshot fills the NAV once published.
        TefasFundDto navlessEtf = new TefasFundDto("ETF1", "name ETF1",
                LocalDateTime.of(2026, 5, 12, 0, 0),
                null, new BigDecimal("690.00"), null, null, null);
        Fund saved = fund("ETF1");
        when(tefasClient.bulkFetch(eq(FundType.BYF), any(), any())).thenReturn(List.of(navlessEtf));
        when(tefasClient.bulkFetch(eq(FundType.YAT), any(), any())).thenReturn(List.of());
        stubTransactionTemplate();
        when(entityWriter.saveSnapshot(any(), eq(FundType.BYF))).thenReturn(saved);

        processor.refreshAll();

        ArgumentCaptor<TefasFundDto> captor = ArgumentCaptor.forClass(TefasFundDto.class);
        verify(entityWriter).saveSnapshot(captor.capture(), eq(FundType.BYF));
        assertThat(captor.getValue().price()).isNull();                       // NAV not faked
        assertThat(captor.getValue().bulletinPrice()).isEqualByComparingTo("690.00");
        verify(entityWriter).ensureByfTracked("ETF1", "name ETF1");           // still tracked → shows in list
    }

    @Test
    void refreshAll_autoTracksAllYat_fromTefasBulk() {
        TefasFundDto first = dto("TI2");
        TefasFundDto second = dto("OTHER");
        Fund savedFirst = fund("TI2");
        Fund savedSecond = fund("OTHER");
        when(tefasClient.bulkFetch(eq(FundType.BYF), any(), any())).thenReturn(List.of());
        when(tefasClient.bulkFetch(eq(FundType.YAT), any(), any())).thenReturn(List.of(first, second));
        stubTransactionTemplate();
        when(entityWriter.saveSnapshot(first, FundType.YAT)).thenReturn(savedFirst);
        when(entityWriter.saveSnapshot(second, FundType.YAT)).thenReturn(savedSecond);

        processor.refreshAll();

        verify(entityWriter).saveSnapshot(first, FundType.YAT);
        verify(entityWriter).saveSnapshot(second, FundType.YAT);
        verify(entityWriter).ensureYatTracked("TI2", "name TI2");
        verify(entityWriter).ensureYatTracked("OTHER", "name OTHER");
    }

    @Test
    void refreshAll_walksBackOneDay_whenFirstAttemptReturnsNoData() {
        when(tefasClient.bulkFetch(eq(FundType.BYF), any(), any()))
                .thenReturn(List.of())
                .thenReturn(List.of(dto("BTC1")));
        when(tefasClient.bulkFetch(eq(FundType.YAT), any(), any())).thenReturn(List.of());
        stubTransactionTemplate();
        when(entityWriter.saveSnapshot(any(), eq(FundType.BYF))).thenReturn(fund("BTC1"));

        processor.refreshAll();

        verify(tefasClient, times(2)).bulkFetch(eq(FundType.BYF), any(), any());
        verify(entityWriter).saveSnapshot(any(), eq(FundType.BYF));
    }

    @Test
    void refreshAll_swallowsCircuitBreakerOpen_andSkipsPersist() {
        CircuitBreaker cb = CircuitBreaker.of("tefas", CircuitBreakerConfig.ofDefaults());
        when(tefasClient.bulkFetch(eq(FundType.BYF), any(), any()))
                .thenThrow(CallNotPermittedException.createCallNotPermittedException(cb));
        when(tefasClient.bulkFetch(eq(FundType.YAT), any(), any()))
                .thenThrow(CallNotPermittedException.createCallNotPermittedException(cb));

        processor.refreshAll();

        verify(entityWriter, never()).saveSnapshot(any(), any());
        verify(fundCacheService, never()).putSnapshot(anyString(), any());
    }

    @Test
    void refreshAll_skipsTrackingAndCache_whenSaveSnapshotReturnsNull() {
        TefasFundDto byfDto = dto("BTC1");
        when(tefasClient.bulkFetch(eq(FundType.BYF), any(), any())).thenReturn(List.of(byfDto));
        when(tefasClient.bulkFetch(eq(FundType.YAT), any(), any())).thenReturn(List.of());
        stubTransactionTemplate();
        when(entityWriter.saveSnapshot(byfDto, FundType.BYF)).thenReturn(null);

        processor.refreshAll();

        verify(entityWriter, never()).ensureByfTracked(anyString(), anyString());
        verify(fundCacheService, never()).putSnapshot(anyString(), any());
    }

    @Test
    void refreshAll_continuesAfterPersistException_andSavesNextItem() {
        TefasFundDto first = dto("ERR");
        TefasFundDto second = dto("OK");
        Fund saved = fund("OK");
        when(tefasClient.bulkFetch(eq(FundType.BYF), any(), any())).thenReturn(List.of(first, second));
        when(tefasClient.bulkFetch(eq(FundType.YAT), any(), any())).thenReturn(List.of());
        stubTransactionTemplate();
        when(entityWriter.saveSnapshot(first, FundType.BYF)).thenThrow(new RuntimeException("boom"));
        when(entityWriter.saveSnapshot(second, FundType.BYF)).thenReturn(saved);

        processor.refreshAll();

        verify(entityWriter).saveSnapshot(second, FundType.BYF);
        verify(fundCacheService).putSnapshot("OK", saved);
    }

    @Test
    void refreshOne_persistsAndCachesYat_whenYatHasData() {
        TefasFundDto found = dto("TI2");
        Fund saved = fund("TI2");
        when(tefasClient.post(eq(FundType.YAT), eq("TI2"), any(), any())).thenReturn(List.of(found));
        stubTransactionTemplate();
        when(entityWriter.saveSnapshot(found, FundType.YAT)).thenReturn(saved);

        processor.refreshOne("TI2");

        verify(entityWriter).upsertCandleFromDto(saved, FundType.YAT, found);
        verify(fundCacheService).putSnapshot("TI2", saved);
        verify(tefasClient, never()).post(eq(FundType.BYF), anyString(), any(), any());
    }

    @Test
    void refreshOne_fallsBackToByf_whenYatEmpty() {
        TefasFundDto found = dto("BTC1");
        Fund saved = fund("BTC1");
        when(tefasClient.post(eq(FundType.YAT), eq("BTC1"), any(), any())).thenReturn(List.of());
        when(tefasClient.post(eq(FundType.BYF), eq("BTC1"), any(), any())).thenReturn(List.of(found));
        stubTransactionTemplate();
        when(entityWriter.saveSnapshot(found, FundType.BYF)).thenReturn(saved);

        processor.refreshOne("BTC1");

        verify(fundCacheService).putSnapshot("BTC1", saved);
    }

    @Test
    void refreshOne_doesNothing_whenBothTypesEmpty() {
        when(tefasClient.post(eq(FundType.YAT), anyString(), any(), any())).thenReturn(List.of());
        when(tefasClient.post(eq(FundType.BYF), anyString(), any(), any())).thenReturn(List.of());

        processor.refreshOne("MISSING");

        verify(entityWriter, never()).saveSnapshot(any(), any());
        verify(fundCacheService, never()).putSnapshot(anyString(), any());
    }

    @Test
    void refreshOne_skipsRun_whenCodeIsBlank() {
        processor.refreshOne("   ");

        verify(tefasClient, never()).post(any(), anyString(), any(), any());
    }

    @Test
    void exists_returnsTrue_whenYatHasData() {
        when(tefasClient.post(eq(FundType.YAT), eq("TI2"), any(), any())).thenReturn(List.of(dto("TI2")));

        boolean result = processor.exists("TI2");

        assertThat(result).isTrue();
    }

    @Test
    void exists_returnsTrue_whenOnlyByfHasData() {
        when(tefasClient.post(eq(FundType.YAT), eq("BTC1"), any(), any())).thenReturn(List.of());
        when(tefasClient.post(eq(FundType.BYF), eq("BTC1"), any(), any())).thenReturn(List.of(dto("BTC1")));

        boolean result = processor.exists("BTC1");

        assertThat(result).isTrue();
    }

    @Test
    void exists_returnsFalse_whenNeitherTypeHasData() {
        when(tefasClient.post(eq(FundType.YAT), eq("X"), any(), any())).thenReturn(List.of());
        when(tefasClient.post(eq(FundType.BYF), eq("X"), any(), any())).thenReturn(List.of());

        boolean result = processor.exists("X");

        assertThat(result).isFalse();
    }

    @Test
    void exists_propagatesTemporarilyUnavailable_whenLookupFailsTransiently() {
        when(tefasClient.post(eq(FundType.YAT), eq("X"), any(), any()))
                .thenThrow(new RuntimeException("WAF block"));

        // A transient upstream failure must NOT be reported as "does not exist"; it propagates so the caller retries.
        assertThatThrownBy(() -> processor.exists("X"))
                .isInstanceOf(com.finance.common.exception.BusinessException.class)
                .hasMessage("error.market.dataTemporarilyUnavailable");
    }

    @Test
    void exists_returnsFalse_whenCodeIsBlank() {
        boolean result = processor.exists("   ");

        assertThat(result).isFalse();
    }
}
