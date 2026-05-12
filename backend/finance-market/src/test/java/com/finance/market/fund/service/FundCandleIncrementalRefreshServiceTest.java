package com.finance.market.fund.service;

import com.finance.common.config.AppProperties;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.fund.client.TefasClient;
import com.finance.market.fund.config.FundProperties;
import com.finance.market.fund.dto.external.TefasFundDto;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundCandle;
import com.finance.market.fund.model.FundType;
import com.finance.market.fund.repository.FundCandleRepository;
import com.finance.market.fund.repository.FundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundCandleIncrementalRefreshServiceTest {

    @Mock private TefasClient tefasClient;
    @Mock private FundRepository fundRepository;
    @Mock private FundCandleRepository fundCandleRepository;
    @SuppressWarnings("unchecked")
    @Mock private MarketCacheService<Fund> fundCacheService;
    @Mock private FundSnapshotProcessor snapshotProcessor;
    @Mock private FundEntityWriter entityWriter;
    @Mock private TransactionTemplate transactionTemplate;

    private FundCandleIncrementalRefreshService service;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        FundProperties fundProperties = new FundProperties();
        service = new FundCandleIncrementalRefreshService(tefasClient, fundRepository,
                fundCandleRepository, fundCacheService, snapshotProcessor, entityWriter,
                transactionTemplate, appProperties, fundProperties);
    }

    @SuppressWarnings("unchecked")
    private void stubTransactionTemplate() {
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
                inv.<TransactionCallback<Integer>>getArgument(0).doInTransaction(null));
    }

    private Fund fund(String code, FundType type) {
        Fund f = new Fund();
        f.setFundCode(code);
        f.setFundType(type);
        return f;
    }

    private TefasFundDto dto(String code) {
        return new TefasFundDto(code, "name", LocalDateTime.now(),
                new BigDecimal("1.50"), null, null, null, null);
    }

    @Test
    void refresh_skipsRun_whenCodeIsBlank() {
        service.refresh("   ");

        verify(fundRepository, never()).findById(anyString());
    }

    @Test
    void refresh_skipsRun_whenSnapshotProcessorAlsoCannotResolveFund() {
        when(fundRepository.findById("MISSING")).thenReturn(Optional.empty());

        service.refresh("MISSING");

        verify(snapshotProcessor).refreshOne("MISSING");
        verify(tefasClient, never()).post(any(), anyString(), any(), any());
    }

    @Test
    void refresh_invokesFullHistory_whenCandleCountBelowMin() {
        Fund f = fund("TI2", FundType.YAT);
        when(fundRepository.findById("TI2")).thenReturn(Optional.of(f));
        when(fundCandleRepository.countByFundCode("TI2")).thenReturn(0L);
        when(tefasClient.post(eq(FundType.YAT), eq("TI2"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(dto("TI2")));
        stubTransactionTemplate();
        when(entityWriter.saveCandleBatch(eq(f), eq(FundType.YAT), any())).thenReturn(1);
        when(fundCandleRepository.findFirstByFundCodeOrderByCandleDateDesc("TI2"))
                .thenReturn(Optional.empty());

        service.refresh("TI2");

        verify(tefasClient, org.mockito.Mockito.atLeastOnce()).post(eq(FundType.YAT), eq("TI2"),
                any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void refresh_invokesIncrementalFetch_whenCandleCountAboveMin_andLastCandleOld() {
        Fund f = fund("TI2", FundType.YAT);
        when(fundRepository.findById("TI2")).thenReturn(Optional.of(f));
        when(fundCandleRepository.countByFundCode("TI2")).thenReturn(2000L);
        FundCandle last = new FundCandle();
        last.setCandleDate(LocalDate.now().minusDays(30).atStartOfDay());
        when(fundCandleRepository.findFirstByFundCodeOrderByCandleDateDesc("TI2"))
                .thenReturn(Optional.of(last));
        when(tefasClient.post(eq(FundType.YAT), eq("TI2"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(dto("TI2")));
        stubTransactionTemplate();
        when(entityWriter.saveCandleBatch(eq(f), eq(FundType.YAT), any())).thenReturn(1);

        service.refresh("TI2");

        verify(tefasClient, org.mockito.Mockito.atLeastOnce()).post(eq(FundType.YAT), eq("TI2"),
                any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void refresh_skipsTefasFetch_whenIncrementalAndLastCandleIsToday() {
        Fund f = fund("TI2", FundType.YAT);
        when(fundRepository.findById("TI2")).thenReturn(Optional.of(f));
        when(fundCandleRepository.countByFundCode("TI2")).thenReturn(2000L);
        FundCandle last = new FundCandle();
        last.setCandleDate(LocalDate.now().plusDays(1).atStartOfDay());
        when(fundCandleRepository.findFirstByFundCodeOrderByCandleDateDesc("TI2"))
                .thenReturn(Optional.of(last));

        service.refresh("TI2");

        verify(tefasClient, never()).post(any(), anyString(), any(), any());
    }

    @Test
    void refresh_defaultsToYat_whenFundTypeUnset() {
        Fund f = fund("TI2", null);
        when(fundRepository.findById("TI2")).thenReturn(Optional.of(f));
        when(fundCandleRepository.countByFundCode("TI2")).thenReturn(0L);
        when(tefasClient.post(eq(FundType.YAT), eq("TI2"), any(), any())).thenReturn(List.of());
        when(fundCandleRepository.findFirstByFundCodeOrderByCandleDateDesc("TI2"))
                .thenReturn(Optional.empty());

        service.refresh("TI2");

        verify(tefasClient, org.mockito.Mockito.atLeastOnce()).post(eq(FundType.YAT), anyString(),
                any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void refresh_updatesCache_whenChangePercentRefreshed() {
        Fund f = fund("TI2", FundType.YAT);
        when(fundRepository.findById("TI2")).thenReturn(Optional.of(f));
        when(fundCandleRepository.countByFundCode("TI2")).thenReturn(2000L);
        FundCandle latest = new FundCandle();
        latest.setCandleDate(LocalDate.now().plusDays(1).atStartOfDay());
        when(fundCandleRepository.findFirstByFundCodeOrderByCandleDateDesc("TI2"))
                .thenReturn(Optional.of(latest));
        when(entityWriter.refreshChangePercent(eq(f), any(LocalDateTime.class))).thenReturn(true);

        service.refresh("TI2");

        verify(fundCacheService).putSnapshot("TI2", f);
    }
}
