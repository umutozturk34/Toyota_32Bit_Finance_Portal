package com.finance.market.forex.service;

import com.finance.market.forex.dto.response.ForexCandleResponse;
import com.finance.market.forex.mapper.ForexResponseMapper;
import com.finance.market.forex.model.ForexCandle;
import com.finance.market.forex.repository.ForexCandleRepository;
import com.finance.market.forex.repository.ForexRepository;
import com.finance.shared.model.CandlePeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForexQueryServiceTest {

    @Mock private ForexCandleRepository forexCandleRepository;
    @Mock private ForexResponseMapper forexResponseMapper;
    @Mock private ForexRepository forexRepository;

    private ForexQueryService service;

    @BeforeEach
    void setUp() {
        service = new ForexQueryService(forexCandleRepository, forexResponseMapper, forexRepository);
    }

    @Test
    void should_returnEmpty_when_currencyRowMissing() {
        when(forexRepository.existsById("XYZ")).thenReturn(false);

        List<ForexCandleResponse> result = service.getHistory("xyz", CandlePeriod.ONE_MONTH);

        assertThat(result).isEmpty();
    }

    @Test
    void should_returnHistory_when_currencyCodeFound() {
        when(forexRepository.existsById("USD")).thenReturn(true);
        ForexCandle candle = ForexCandle.builder().currencyCode("USD")
                .candleDate(LocalDateTime.of(2026, 5, 11, 0, 0))
                .sellingPrice(new BigDecimal("45.27"))
                .buyingPrice(new BigDecimal("45.19"))
                .build();
        when(forexCandleRepository.findByCurrencyCodeAndCandleDateBetweenOrderByCandleDateAsc(
                anyString(), any(), any())).thenReturn(List.of(candle));
        when(forexResponseMapper.toForexCandleResponses(any()))
                .thenReturn(List.of(new ForexCandleResponse(LocalDateTime.now(),
                        new BigDecimal("45.27"), new BigDecimal("45.27"), new BigDecimal("45.27"),
                        new BigDecimal("45.27"), new BigDecimal("45.27"), new BigDecimal("45.19"),
                        new BigDecimal("45.15"), new BigDecimal("45.33"))));

        List<ForexCandleResponse> result = service.getHistory("USD", CandlePeriod.ONE_MONTH);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().sellingPrice()).isEqualByComparingTo("45.27");
        assertThat(result.getFirst().buyingPrice()).isEqualByComparingTo("45.19");
    }

    @Test
    void should_returnHistoryRange_when_explicitDatesPassed() {
        when(forexRepository.existsById("USD")).thenReturn(true);
        when(forexCandleRepository.findByCurrencyCodeAndCandleDateBetweenOrderByCandleDateAsc(
                anyString(), any(), any())).thenReturn(List.of());
        when(forexResponseMapper.toForexCandleResponses(any())).thenReturn(List.of());

        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 11);
        List<ForexCandleResponse> result = service.getHistoryInRange("USD", from, to);

        assertThat(result).isEmpty();
    }

    @Test
    void should_normalizeCurrencyCode_when_inputHasMixedCase() {
        when(forexRepository.existsById("USD")).thenReturn(true);
        when(forexCandleRepository.findByCurrencyCodeAndCandleDateBetweenOrderByCandleDateAsc(
                anyString(), any(), any())).thenReturn(List.of());
        when(forexResponseMapper.toForexCandleResponses(any())).thenReturn(List.of());

        service.getHistory("  usd  ", CandlePeriod.ONE_MONTH);

        org.mockito.Mockito.verify(forexRepository).existsById("USD");
    }
}
