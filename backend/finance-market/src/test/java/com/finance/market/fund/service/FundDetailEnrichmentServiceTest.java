package com.finance.market.fund.service;

import com.finance.market.fund.client.TefasClient;
import com.finance.market.fund.dto.external.TefasFundAllocationDto;
import com.finance.market.fund.dto.external.TefasFundInfoDto;
import com.finance.market.fund.dto.external.TefasFundProfileDto;
import com.finance.market.fund.dto.external.TefasFundReturnsDto;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundAllocation;
import com.finance.market.fund.model.FundType;
import com.finance.market.fund.repository.FundAllocationRepository;
import com.finance.market.fund.repository.FundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FundDetailEnrichmentServiceTest {

    @Mock private TefasClient tefasClient;
    @Mock private FundRepository fundRepository;
    @Mock private FundAllocationRepository allocationRepository;
    @Mock private com.finance.market.core.service.TrackedAssetQueryService trackedAssetQueryService;
    @SuppressWarnings("unchecked")
    @Mock private com.finance.market.core.cache.MarketCacheService<Fund> fundCacheService;

    private FundDetailEnrichmentService service;

    @BeforeEach
    void setUp() {
        service = new FundDetailEnrichmentService(tefasClient, fundRepository, allocationRepository, trackedAssetQueryService, fundCacheService);
        when(fundRepository.save(any(Fund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fundRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void should_applyReturnsAndRisk_when_tefasReturnsResponseMapsToExistingFund() {
        Fund fund = Fund.builder().fundCode("AAL").fundType(FundType.YAT).build();
        when(trackedAssetQueryService.getCodes(com.finance.common.model.TrackedAssetType.FUND)).thenReturn(List.of("AAL"));
        when(fundRepository.findAllById(List.of("AAL"))).thenReturn(List.of(fund));
        TefasFundReturnsDto dto = new TefasFundReturnsDto("AAL", "ATA",
                "Para Piyasası Şemsiye Fonu", true,
                new BigDecimal("3.25"), new BigDecimal("9.43"), new BigDecimal("20.33"),
                new BigDecimal("50.13"), new BigDecimal("14.61"),
                new BigDecimal("243.52"), new BigDecimal("380.52"), "3");
        when(tefasClient.fetchReturns(FundType.YAT)).thenReturn(List.of(dto));
        when(tefasClient.fetchReturns(FundType.BYF)).thenReturn(List.of());

        int updated = service.enrichReturnsAndRisk();

        assertThat(updated).isEqualTo(1);
        assertThat(fund.getReturn1m()).isEqualByComparingTo("3.25");
        assertThat(fund.getReturn1y()).isEqualByComparingTo("50.13");
        assertThat(fund.getRiskValue()).isEqualTo(3);
        assertThat(fund.getSubCategory()).isEqualTo("Para Piyasası Şemsiye Fonu");
    }

    @Test
    void should_skipUnknownFund_when_returnsRowReferencesCodeNotInDb() {
        when(trackedAssetQueryService.getCodes(com.finance.common.model.TrackedAssetType.FUND)).thenReturn(List.of("AAL"));
        TefasFundReturnsDto dto = new TefasFundReturnsDto("XXX", "X PORTFOY YENI FON",
                "Hisse Şemsiye Fonu", true,
                new BigDecimal("5.0"), null, null, null, null, null, null, "4");
        when(tefasClient.fetchReturns(FundType.YAT)).thenReturn(List.of(dto));
        when(tefasClient.fetchReturns(FundType.BYF)).thenReturn(List.of());

        int updated = service.enrichReturnsAndRisk();

        assertThat(updated).isZero();
        verify(fundRepository, never()).save(any());
        verify(fundRepository, never()).saveAll(any());
    }

    @Test
    void should_returnZero_when_returnsEndpointThrowsForBothFundTypes() {
        when(trackedAssetQueryService.getCodes(com.finance.common.model.TrackedAssetType.FUND)).thenReturn(List.of("AAL"));
        when(tefasClient.fetchReturns(any(FundType.class))).thenThrow(new RuntimeException("network"));

        int updated = service.enrichReturnsAndRisk();

        assertThat(updated).isZero();
    }

    @Test
    void should_replaceAllocations_when_freshAllocationResponseArrivesForKnownFund() {
        when(trackedAssetQueryService.getCodes(com.finance.common.model.TrackedAssetType.FUND)).thenReturn(List.of("AAL"));
        TefasFundAllocationDto dto = new TefasFundAllocationDto();
        dto.putUnknown("fb", 30.8);
        dto.putUnknown("tr", 52.13);
        dto.putUnknown("vmtl", 9.7);
        setField(dto, "fundCode", "AAL");
        when(tefasClient.fetchAllocations(eq(FundType.YAT), any())).thenReturn(List.of(dto));
        when(tefasClient.fetchAllocations(eq(FundType.BYF), any())).thenReturn(List.of());

        int updated = service.enrichAllocations(LocalDate.of(2026, 5, 16));

        assertThat(updated).isEqualTo(1);
        verify(allocationRepository).deleteByFundCodeIn(Set.of("AAL"));
        ArgumentCaptor<List<FundAllocation>> captor = ArgumentCaptor.forClass(List.class);
        verify(allocationRepository).saveAll(captor.capture());
        List<FundAllocation> saved = captor.getValue();
        assertThat(saved).hasSize(3);
        assertThat(saved).extracting(FundAllocation::getAssetClass).containsExactly("fb", "tr", "vmtl");
    }

    @Test
    void should_skipFundWithoutAllocations_when_allRowsAreEmpty() {
        when(trackedAssetQueryService.getCodes(com.finance.common.model.TrackedAssetType.FUND)).thenReturn(List.of("AAL"));
        TefasFundAllocationDto dto = new TefasFundAllocationDto();
        setField(dto, "fundCode", "AAL");
        when(tefasClient.fetchAllocations(any(FundType.class), any())).thenReturn(List.of(dto));

        int updated = service.enrichAllocations(LocalDate.of(2026, 5, 16));

        assertThat(updated).isZero();
        verify(allocationRepository, never()).saveAll(any());
        verify(allocationRepository, never()).deleteByFundCodeIn(anyCollection());
    }

    @Test
    void should_walkBackToPriorBusinessDay_when_todaysAllocationEmpty() {
        when(trackedAssetQueryService.getCodes(com.finance.common.model.TrackedAssetType.FUND)).thenReturn(List.of("AAL"));
        TefasFundAllocationDto dto = new TefasFundAllocationDto();
        dto.putUnknown("hs", 100.0);
        setField(dto, "fundCode", "AAL");
        // Saturday (2026-05-16) empty, Friday (2026-05-15) has data
        when(tefasClient.fetchAllocations(eq(FundType.YAT), eq(LocalDate.of(2026, 5, 16)))).thenReturn(List.of());
        when(tefasClient.fetchAllocations(eq(FundType.YAT), eq(LocalDate.of(2026, 5, 15)))).thenReturn(List.of(dto));
        when(tefasClient.fetchAllocations(eq(FundType.BYF), any())).thenReturn(List.of());

        int updated = service.enrichAllocations(LocalDate.of(2026, 5, 16));

        assertThat(updated).isEqualTo(1);
        verify(allocationRepository).deleteByFundCodeIn(Set.of("AAL"));
    }

    @Test
    void should_skipAllocationForUnknownFund_when_fundCodeNotInDb() {
        when(trackedAssetQueryService.getCodes(com.finance.common.model.TrackedAssetType.FUND)).thenReturn(List.of("AAL"));
        TefasFundAllocationDto dto = new TefasFundAllocationDto();
        dto.putUnknown("hs", 100.0);
        setField(dto, "fundCode", "ZZZ");
        when(tefasClient.fetchAllocations(any(FundType.class), any())).thenReturn(List.of(dto));

        int updated = service.enrichAllocations(LocalDate.of(2026, 5, 16));

        assertThat(updated).isZero();
        verify(allocationRepository, never()).saveAll(any());
        verify(allocationRepository, never()).deleteByFundCodeIn(anyCollection());
    }

    @Test
    void should_applyInfoAndProfile_when_singleFundEnrichmentSucceeds() {
        Fund fund = Fund.builder().fundCode("AAL").fundType(FundType.YAT).build();
        when(fundRepository.findById("AAL")).thenReturn(Optional.of(fund));
        TefasFundInfoDto info = new TefasFundInfoDto("AAL", "ATA",
                new BigDecimal("3.23"), new BigDecimal("0.10"), null, null,
                "Para Piyasası Fonu", 22, 83, null, new BigDecimal("0.18"));
        TefasFundProfileDto profile = new TefasFundProfileDto("AAL", "ATA",
                "TRMAALWWWWW5", "https://kap.org.tr/aal", "TEFAS'ta işlem görüyor",
                BigDecimal.TEN, BigDecimal.TEN, null, null, null, null,
                "09:00", "13:30", 0, 0, null, "1");
        when(tefasClient.fetchInfo(FundType.YAT, "AAL")).thenReturn(info);
        when(tefasClient.fetchProfile(FundType.YAT, "AAL")).thenReturn(profile);

        Fund result = service.enrichSingleFundDetails("AAL");

        assertThat(result).isNotNull();
        assertThat(fund.getCategory()).isEqualTo("Para Piyasası Fonu");
        assertThat(fund.getCategoryRank()).isEqualTo(22);
        assertThat(fund.getCategoryTotalFunds()).isEqualTo(83);
        assertThat(fund.getMarketShare()).isEqualByComparingTo("0.18");
        assertThat(fund.getIsinCode()).isEqualTo("TRMAALWWWWW5");
        assertThat(fund.getKapLink()).isEqualTo("https://kap.org.tr/aal");
        assertThat(fund.getRiskValue()).isEqualTo(1);
    }

    @Test
    void should_returnNull_when_singleFundCodeDoesNotExist() {
        when(fundRepository.findById("ZZZ")).thenReturn(Optional.empty());

        Fund result = service.enrichSingleFundDetails("ZZZ");

        assertThat(result).isNull();
        verify(tefasClient, never()).fetchInfo(any(), anyString());
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
