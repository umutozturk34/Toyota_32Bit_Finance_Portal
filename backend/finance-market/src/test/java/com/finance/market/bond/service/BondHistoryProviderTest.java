package com.finance.market.bond.service;

import com.finance.common.model.MarketType;
import com.finance.market.bond.dto.response.BondRateResponse;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.bond.repository.BondRepository;
import com.finance.shared.model.CandlePeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BondHistoryProviderTest {

    @Mock private BondRepository bondRepository;
    @Mock private BondRateHistoryRepository rateHistoryRepository;
    @InjectMocks private BondHistoryProvider provider;

    @Test
    void getMarketType_isBond() {
        // Act + Assert
        assertThat(provider.getMarketType()).isEqualTo(MarketType.BOND);
    }

    @Test
    void getHistory_resolvesSeriesToIsin_andReturnsTryPricePointsDroppingNullPrices() {
        // Arrange
        Bond bond = new Bond();
        bond.setSeriesCode("TRT250101T15");
        bond.setIsinCode("ISIN1");
        when(bondRepository.findById("TRT250101T15")).thenReturn(Optional.of(bond));
        when(rateHistoryRepository.findByIsinCodeAndRateDateAfterOrderByRateDateAsc(eq("ISIN1"), any()))
                .thenReturn(List.of(
                        rate(LocalDate.of(2026, 1, 2), new BigDecimal("18.25"), new BigDecimal("98.40")),
                        rate(LocalDate.of(2026, 1, 3), new BigDecimal("18.10"), null),
                        rate(LocalDate.of(2026, 1, 4), new BigDecimal("18.00"), new BigDecimal("99.10"))));

        // Act
        List<BondRateResponse> points = provider.getHistory("TRT250101T15", CandlePeriod.ONE_YEAR);

        // Assert — only real prices survive, in ascending date order, TRY verbatim
        assertThat(points).extracting(BondRateResponse::price)
                .containsExactly(new BigDecimal("98.40"), new BigDecimal("99.10"));
    }

    @Test
    void getHistoryInRange_excludesPointsAfterTo() {
        // Arrange
        Bond bond = new Bond();
        bond.setSeriesCode("TRT250101T15");
        bond.setIsinCode("ISIN1");
        when(bondRepository.findById("TRT250101T15")).thenReturn(Optional.of(bond));
        when(rateHistoryRepository.findByIsinCodeAndRateDateAfterOrderByRateDateAsc(eq("ISIN1"), any()))
                .thenReturn(List.of(
                        rate(LocalDate.of(2026, 1, 2), new BigDecimal("18.25"), new BigDecimal("98.40")),
                        rate(LocalDate.of(2026, 2, 1), new BigDecimal("18.00"), new BigDecimal("99.10"))));

        // Act
        List<BondRateResponse> points = provider.getHistoryInRange(
                "TRT250101T15", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15));

        // Assert — the Feb point is past `to` and dropped
        assertThat(points).extracting(BondRateResponse::date)
                .containsExactly(LocalDate.of(2026, 1, 2));
    }

    @Test
    void getHistory_returnsEmpty_whenSeriesCodeUnknown() {
        // Arrange
        when(bondRepository.findById("MISSING")).thenReturn(Optional.empty());

        // Act + Assert
        assertThat(provider.getHistory("MISSING", CandlePeriod.ONE_YEAR)).isEmpty();
    }

    private BondRateHistory rate(LocalDate date, BigDecimal coupon, BigDecimal price) {
        BondRateHistory h = new BondRateHistory();
        h.setRateDate(date);
        h.setCouponRate(coupon);
        h.setPrice(price);
        return h;
    }
}
