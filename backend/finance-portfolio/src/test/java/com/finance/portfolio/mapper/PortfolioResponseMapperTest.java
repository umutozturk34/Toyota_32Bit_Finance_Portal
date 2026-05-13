package com.finance.portfolio.mapper;

import com.finance.portfolio.dto.response.AllocationItem;
import com.finance.portfolio.dto.response.PortfolioSummaryResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioResponseMapperTest {

    private final PortfolioResponseMapper mapper = new PortfolioResponseMapperImpl();

    @Test
    void toPositionResponse_packsAllArgumentsIntoResponse() {
        com.finance.portfolio.model.PortfolioPosition pos = com.finance.portfolio.model.PortfolioPosition.builder()
                .id(7L)
                .assetType(com.finance.portfolio.model.AssetType.STOCK)
                .assetCode("AKBNK")
                .quantity(new BigDecimal("10"))
                .entryDate(java.time.LocalDateTime.of(2026, 1, 1, 10, 0))
                .entryPrice(new BigDecimal("50"))
                .build();

        com.finance.portfolio.dto.response.PositionResponse response = mapper.toPositionResponse(
                pos, new BigDecimal("60"), new BigDecimal("500"), new BigDecimal("600"),
                new BigDecimal("100"), new BigDecimal("20"), "Akbank", "akbank.png");

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.assetCode()).isEqualTo("AKBNK");
        assertThat(response.assetName()).isEqualTo("Akbank");
        assertThat(response.assetImage()).isEqualTo("akbank.png");
        assertThat(response.entryPrice()).isEqualByComparingTo("50");
        assertThat(response.currentPriceTry()).isEqualByComparingTo("60");
        assertThat(response.marketValueTry()).isEqualByComparingTo("600");
        assertThat(response.pnlPercent()).isEqualByComparingTo("20");
    }

    @Test
    void toPositionResponseShell_usesZerosAndComputedEntryValue() {
        com.finance.portfolio.model.PortfolioPosition pos = com.finance.portfolio.model.PortfolioPosition.builder()
                .id(8L)
                .assetType(com.finance.portfolio.model.AssetType.CRYPTO)
                .assetCode("bitcoin")
                .quantity(new BigDecimal("2"))
                .entryDate(java.time.LocalDateTime.now())
                .entryPrice(new BigDecimal("1000"))
                .build();

        com.finance.portfolio.dto.response.PositionResponse response = mapper.toPositionResponseShell(pos);

        assertThat(response.currentPriceTry()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.pnlTry()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.entryValueTry()).isEqualByComparingTo("2000.0000");
        assertThat(response.assetName()).isNull();
        assertThat(response.assetImage()).isNull();
    }

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
