package com.finance.market.viop.service;

import com.finance.common.model.MarketType;
import com.finance.market.viop.dto.ViopContractSpec;
import com.finance.market.viop.dto.ViopQuoteSnapshot;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.market.viop.port.ViopMarketDataPort;
import com.finance.market.viop.repository.ViopContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ViopDataServiceTest {

    @Mock private ViopMarketDataPort marketData;
    @Mock private ViopEntityWriter entityWriter;
    @Mock private ViopContractRepository contractRepository;
    @Mock private ViopHistoryProvider historyProvider;

    private ViopDataService service;

    @BeforeEach
    void setUp() {
        service = new ViopDataService(marketData, entityWriter, contractRepository, historyProvider);
    }

    @Test
    void should_returnViopMarketType_when_getMarketTypeCalled() {
        assertThat(service.getMarketType()).isEqualTo(MarketType.VIOP);
    }

    @Test
    void should_fetchAndApplyLiveSnapshots_when_refreshLiveSnapshotsCalled() {
        List<ViopQuoteSnapshot> snaps = List.of(
                org.mockito.Mockito.mock(ViopQuoteSnapshot.class));
        when(marketData.fetchAllLiveSnapshots()).thenReturn(snaps);
        when(entityWriter.applyBulkSnapshots(snaps)).thenReturn(Set.of("F_X", "F_Y"));

        Set<String> result = service.refreshLiveSnapshots();

        assertThat(result).containsExactlyInAnyOrder("F_X", "F_Y");
    }

    @Test
    void should_returnEmptySet_when_upstreamReturnsNoSnapshots() {
        when(marketData.fetchAllLiveSnapshots()).thenReturn(List.of());

        Set<String> result = service.refreshLiveSnapshots();

        assertThat(result).isEmpty();
    }

    @Test
    void should_delegateToEntityWriter_when_deactivateStaleCalled() {
        when(entityWriter.deactivateNotIn(Set.of("F_X"))).thenReturn(3);

        int count = service.deactivateStale(Set.of("F_X"));

        assertThat(count).isEqualTo(3);
    }

    @Test
    void should_enrichSpecsFromUpstream_when_enrichSpecsCalled() {
        List<ViopContractSpec> specs = List.of();
        when(marketData.fetchFutureContractSpecs()).thenReturn(specs);
        when(entityWriter.enrichSpecs(specs)).thenReturn(7);

        int enriched = service.enrichSpecs();

        assertThat(enriched).isEqualTo(7);
    }

    @Test
    void should_enrichEachMissingPrice_when_enrichMissingPricesCalled() {
        when(contractRepository.findActiveSymbolsWithoutPrice()).thenReturn(List.of("F_X", "F_Y"));
        when(marketData.fetchSnapshot(anyString())).thenReturn(org.mockito.Mockito.mock(ViopQuoteSnapshot.class));

        int enriched = service.enrichMissingPrices();

        assertThat(enriched).isEqualTo(2);
        verify(entityWriter).applySnapshot(org.mockito.ArgumentMatchers.eq("F_X"), any());
        verify(entityWriter).applySnapshot(org.mockito.ArgumentMatchers.eq("F_Y"), any());
    }

    @Test
    void should_swallowFetchException_when_priceEnrichmentSnapshotFails() {
        when(contractRepository.findActiveSymbolsWithoutPrice()).thenReturn(List.of("F_BAD"));
        when(marketData.fetchSnapshot("F_BAD")).thenThrow(new RuntimeException("fetch fail"));

        int enriched = service.enrichMissingPrices();

        assertThat(enriched).isZero();
    }

    @Test
    void should_invokeMarkExpiredOnEntityWriter_when_sweepExpiredCalled() {
        when(entityWriter.markExpired(any(LocalDate.class))).thenReturn(4);

        int swept = service.sweepExpired();

        assertThat(swept).isEqualTo(4);
    }

    @Test
    void should_persistSnapshotAndCandleRefresh_when_singleRefreshCalled() {
        ViopQuoteSnapshot snap = org.mockito.Mockito.mock(ViopQuoteSnapshot.class);
        when(marketData.fetchSnapshot("F_X")).thenReturn(snap);
        when(historyProvider.refreshCandlesUpTo(anyString(), any())).thenReturn(1);

        service.refresh("F_X");

        verify(entityWriter).applySnapshot("F_X", snap);
        verify(historyProvider).refreshCandlesUpTo("F_X", LocalDate.now());
    }

    @Test
    void should_tolerateCandleFailure_when_refreshThrows() {
        ViopQuoteSnapshot snap = org.mockito.Mockito.mock(ViopQuoteSnapshot.class);
        when(marketData.fetchSnapshot("F_X")).thenReturn(snap);
        when(historyProvider.refreshCandlesUpTo(anyString(), any())).thenThrow(new RuntimeException("history down"));

        service.refresh("F_X");

        verify(entityWriter).applySnapshot("F_X", snap);
    }

    @Test
    void should_runFullPipeline_when_refreshAllCalled() {
        when(marketData.fetchAllLiveSnapshots()).thenReturn(List.of());
        when(entityWriter.applyBulkSnapshots(any())).thenReturn(Set.of());
        when(entityWriter.deactivateNotIn(any())).thenReturn(0);
        when(marketData.fetchFutureContractSpecs()).thenReturn(List.of());
        when(entityWriter.enrichSpecs(any())).thenReturn(0);
        when(contractRepository.findActiveSymbolsWithoutPrice()).thenReturn(List.of());
        when(entityWriter.markExpired(any())).thenReturn(0);
        when(contractRepository.findAll(any(Specification.class))).thenReturn(List.of());

        service.refreshAll();

        verify(marketData).fetchAllLiveSnapshots();
        verify(entityWriter).deactivateNotIn(any());
        verify(entityWriter).enrichSpecs(any());
        verify(entityWriter).markExpired(any());
    }

    @Test
    void should_iterateActiveContractsForCandleSync_when_syncCandlesFromLastStoredCalled() {
        ViopContract c = ViopContract.builder().symbol("F_X").kind(ViopContractKind.FUTURE)
                .active(true).lastPrice(new BigDecimal("35"))
                .lastUpdated(java.time.LocalDateTime.now()).build();
        when(contractRepository.findAll(any(Specification.class))).thenReturn(List.of(c));
        when(historyProvider.refreshCandlesUpTo(anyString(), any())).thenReturn(1);
        when(historyProvider.upsertTodayCandle(anyString(), any())).thenReturn(1);

        int persisted = service.syncCandlesFromLastStored();

        assertThat(persisted).isEqualTo(2);
    }

    @Test
    void should_skipTodayCandleUpsert_when_lastPriceIsStaleFromBeforeToday() {
        ViopContract c = ViopContract.builder().symbol("F_X").kind(ViopContractKind.FUTURE)
                .active(true).lastPrice(new BigDecimal("35"))
                .lastUpdated(java.time.LocalDateTime.now().minusDays(1)).build();
        when(contractRepository.findAll(any(Specification.class))).thenReturn(List.of(c));
        when(historyProvider.refreshCandlesUpTo(anyString(), any())).thenReturn(0);

        int persisted = service.syncCandlesFromLastStored();

        assertThat(persisted).isEqualTo(0);
        verify(historyProvider, org.mockito.Mockito.never()).upsertTodayCandle(anyString(), any());
    }
}
