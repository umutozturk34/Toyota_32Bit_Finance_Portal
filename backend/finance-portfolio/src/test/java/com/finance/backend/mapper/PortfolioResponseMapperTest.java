package com.finance.backend.mapper;

import com.finance.backend.dto.response.AllocationItem;
import com.finance.backend.dto.response.PortfolioSummaryResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioResponseMapperTest {

    private final PortfolioResponseMapper mapper = new PortfolioResponseMapperImpl();

    @Test
    void should_packArgumentsIntoSummary_when_callingToSummaryResponse() {
        BigDecimal total = new BigDecimal("12500.00");
        BigDecimal entry = new BigDecimal("10000.00");
        BigDecimal pnl = new BigDecimal("2500.00");
        BigDecimal pnlPct = new BigDecimal("25.00");
        BigDecimal dailyPnl = new BigDecimal("125.00");
        BigDecimal dailyPnlPct = new BigDecimal("1.00");

        PortfolioSummaryResponse response = mapper.toSummaryResponse(
                total, entry, pnl, pnlPct, dailyPnl, dailyPnlPct);

        assertThat(response.totalValueTry()).isEqualByComparingTo(total);
        assertThat(response.totalEntryValueTry()).isEqualByComparingTo(entry);
        assertThat(response.totalPnlTry()).isEqualByComparingTo(pnl);
        assertThat(response.pnlPercent()).isEqualByComparingTo(pnlPct);
        assertThat(response.dailyPnlTry()).isEqualByComparingTo(dailyPnl);
        assertThat(response.dailyPnlPercent()).isEqualByComparingTo(dailyPnlPct);
    }

    @Test
    void should_supportNullDailyPnl_when_buildingSummary() {
        PortfolioSummaryResponse response = mapper.toSummaryResponse(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null);

        assertThat(response.dailyPnlTry()).isNull();
        assertThat(response.dailyPnlPercent()).isNull();
    }

    @Test
    void should_packArgumentsIntoAllocation_when_callingToAllocationItem() {
        AllocationItem item = mapper.toAllocationItem(
                "BTC", "CRYPTO", new BigDecimal("5000.00"), new BigDecimal("40.00"));

        assertThat(item.label()).isEqualTo("BTC");
        assertThat(item.assetType()).isEqualTo("CRYPTO");
        assertThat(item.valueTry()).isEqualByComparingTo("5000.00");
        assertThat(item.percent()).isEqualByComparingTo("40.00");
    }
}
