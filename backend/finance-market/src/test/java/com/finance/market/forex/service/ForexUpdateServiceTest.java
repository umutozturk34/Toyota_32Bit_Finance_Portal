package com.finance.market.forex.service;

import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.core.service.TrackedAssetCommandService;
import com.finance.market.forex.client.EvdsForexClient;
import com.finance.market.forex.config.ForexProperties;
import com.finance.market.forex.mapper.ForexEvdsMapper;
import com.finance.market.forex.model.Forex;
import com.finance.market.forex.repository.ForexRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForexUpdateServiceTest {

    @Mock private EvdsForexClient evdsClient;
    @Mock private EvdsForexCurrencyResolver currencyResolver;
    @Mock private ForexSnapshotProcessor snapshotProcessor;
    @Mock private ForexEntityWriter entityWriter;
    @Mock private ForexRepository forexRepository;
    @Mock private TrackedAssetCommandService trackedAssetCommandService;
    @Mock private com.finance.market.bank.service.BankRatesService bankRatesService;

    private final ForexEvdsMapper evdsMapper = new ForexEvdsMapper();
    private final ForexProperties forexProperties = new ForexProperties();
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate =
            new org.springframework.transaction.support.TransactionTemplate(
                    new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
                        @Override protected Object doGetTransaction() { return new Object(); }
                        @Override protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {}
                        @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {}
                        @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {}
                    });
    private ForexUpdateService service;

    @BeforeEach
    void setUp() {
        forexProperties.setBackfillStartDate(LocalDate.of(2024, 1, 1));
        service = new ForexUpdateService(evdsClient, currencyResolver, snapshotProcessor, entityWriter,
                evdsMapper, forexRepository, trackedAssetCommandService, forexProperties, transactionTemplate,
                bankRatesService);
    }

    @Test
    void should_skipRefresh_when_evdsReturnsNoActiveCurrencies() {
        stubResolver(List.of());

        service.refreshAll();

        verify(trackedAssetCommandService, never()).autoTrack(any(), anyString(), anyString(), anyInt());
        verify(snapshotProcessor, never()).applyLatestSnapshot(any(), any());
    }

    @Test
    void should_abortRefresh_when_snapshotFetchReturnsNull() {
        ForexSerieMetadata usd = meta("USD", true);
        stubResolver(List.of(usd));
        when(evdsClient.fetchForexData(any(), anyString(), anyString())).thenReturn(null);

        service.refreshAll();

        verify(trackedAssetCommandService, never()).autoTrack(any(), anyString(), anyString(), anyInt());
        verify(snapshotProcessor, never()).applyLatestSnapshot(any(), any());
    }

    @Test
    void should_applyLatestSnapshotToEachCurrency_when_smokeFilterPasses() {
        ForexSerieMetadata usd = meta("USD", true);
        ForexSerieMetadata eur = meta("EUR", true);
        stubResolver(List.of(usd, eur));
        EvdsDataResponse response = new EvdsDataResponse(2, List.of(Map.of(
                "Tarih", LocalDate.now().toString(),
                "TP_DK_USD_S_YTL", "45.27",
                "TP_DK_EUR_S_YTL", "53.20")));
        when(evdsClient.fetchForexData(any(), anyString(), anyString())).thenReturn(response);
        when(snapshotProcessor.findLastCandleDate(anyString())).thenReturn(Optional.of(LocalDate.now()));
        when(forexRepository.findById(anyString())).thenReturn(Optional.of(Forex.builder().currencyCode("X").build()));

        service.refreshAll();

        verify(trackedAssetCommandService).autoTrack(eq(TrackedAssetType.FOREX), eq("USD"), anyString(), anyInt());
        verify(trackedAssetCommandService).autoTrack(eq(TrackedAssetType.FOREX), eq("EUR"), anyString(), anyInt());
        verify(snapshotProcessor).applyLatestSnapshot(eq(usd), eq(response));
        verify(snapshotProcessor).applyLatestSnapshot(eq(eur), eq(response));
    }

    @Test
    void should_dropDeadCurrencies_when_smokeFilterFindsNoData() {
        ForexSerieMetadata bgn = meta("BGN", false);
        stubResolver(List.of(bgn));
        EvdsDataResponse emptyData = new EvdsDataResponse(1, List.of(Map.of("Tarih", LocalDate.now().toString())));
        when(evdsClient.fetchForexData(any(), anyString(), anyString())).thenReturn(emptyData);

        service.refreshAll();

        verify(trackedAssetCommandService, never()).autoTrack(any(), anyString(), anyString(), anyInt());
        verify(snapshotProcessor, never()).applyLatestSnapshot(any(), any());
    }

    @Test
    void should_returnTrue_when_isActiveCurrencyResolverConfirms() {
        when(evdsClient.fetchDovizSerieList()).thenReturn(List.of());
        when(currencyResolver.isActiveCurrencyCode(any(), eq("USD"))).thenReturn(true);

        assertThat(service.isActiveCurrency("USD")).isTrue();
    }

    @Test
    void should_returnFalse_when_isActiveCurrencyBlank() {
        assertThat(service.isActiveCurrency("")).isFalse();
        assertThat(service.isActiveCurrency(null)).isFalse();
    }

    @Test
    void should_delegateToSnapshotProcessor_when_refreshSingleCurrency() {
        service.refresh("USD");

        verify(snapshotProcessor).refreshOne("USD");
    }

    private void stubResolver(List<ForexSerieMetadata> active) {
        when(evdsClient.fetchDovizSerieList()).thenReturn(List.of());
        when(evdsClient.fetchEfektifSerieList()).thenReturn(List.of());
        when(currencyResolver.resolveActive(any(), any())).thenReturn(active);
    }

    private ForexSerieMetadata meta(String code, boolean hasEfektif) {
        return new ForexSerieMetadata(code, code + " Name", code + " Adı", 1, hasEfektif);
    }
}
