package com.finance.market.fund.service;

import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.fund.dto.response.FundCandleResponse;
import com.finance.market.fund.mapper.FundResponseMapper;
import com.finance.market.fund.model.FundCandle;
import com.finance.market.fund.repository.FundCandleRepository;
import com.finance.shared.model.CandlePeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundQueryServiceTest {

    @Mock private FundCandleRepository fundCandleRepository;
    @Mock private FundResponseMapper fundResponseMapper;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;

    private FundQueryService service;

    @BeforeEach
    void setUp() {
        service = new FundQueryService(fundCandleRepository, fundResponseMapper, trackedAssetQueryService);
    }

    @Test
    void getMarketType_returnsFund() {
        assertThat(service.getMarketType()).isEqualTo(MarketType.FUND);
    }

    @Test
    void getHistory_resolvesCode_andDelegatesToRepository() {
        List<FundCandle> candles = List.of(new FundCandle());
        List<FundCandleResponse> mapped = List.of();
        when(trackedAssetQueryService.resolveCodeOrThrow(TrackedAssetType.FUND, "aal"))
                .thenReturn("AAL");
        when(fundCandleRepository.findByFundCodeAndCandleDateBetweenOrderByCandleDateAsc(
                eq("AAL"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(candles);
        when(fundResponseMapper.toFundCandleResponses(candles)).thenReturn(mapped);

        List<FundCandleResponse> result = service.getHistory("aal", CandlePeriod.ONE_MONTH);

        assertThat(result).isSameAs(mapped);
        verify(fundResponseMapper).toFundCandleResponses(candles);
    }

    @Test
    void getHistoryInRange_passesStartAndEndOfDay() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 5);
        when(trackedAssetQueryService.resolveCodeOrThrow(TrackedAssetType.FUND, "bbb"))
                .thenReturn("BBB");
        when(fundCandleRepository.findByFundCodeAndCandleDateBetweenOrderByCandleDateAsc(
                eq("BBB"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(fundResponseMapper.toFundCandleResponses(List.of())).thenReturn(List.of());

        service.getHistoryInRange("bbb", from, to);

        ArgumentCaptor<LocalDateTime> fromCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(fundCandleRepository).findByFundCodeAndCandleDateBetweenOrderByCandleDateAsc(
                eq("BBB"), fromCap.capture(), toCap.capture());
        assertThat(fromCap.getValue()).isEqualTo(from.atStartOfDay());
        assertThat(toCap.getValue()).isEqualTo(to.atTime(LocalTime.MAX));
    }
}
