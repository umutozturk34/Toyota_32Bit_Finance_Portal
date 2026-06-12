package com.finance.portfolio.service.summary;

import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioPosition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SpotTotalsTest {

    @Test
    void should_startAtZero_when_freshlyConstructed() {
        SpotTotals totals = new SpotTotals();

        assertThat(totals.spotValue).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(totals.closedExitValue).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(totals.openCost).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(totals.closedCost).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_addEntryCostAndExitCash_when_closedLotHasExitPrice() {
        SpotTotals totals = new SpotTotals();
        PortfolioPosition closed = position(new BigDecimal("10"), new BigDecimal("40"));
        closed.closeWith(LocalDateTime.now(), new BigDecimal("60"));

        totals.addClosed(closed);

        assertThat(totals.closedCost).isEqualByComparingTo("400");
        assertThat(totals.closedExitValue).isEqualByComparingTo("600");
        assertThat(totals.spotValue).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(totals.openCost).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_addEntryCostButNoExitCash_when_closedLotHasNullExitPrice() {
        SpotTotals totals = new SpotTotals();
        PortfolioPosition closed = position(new BigDecimal("5"), new BigDecimal("80"));
        closed.closeWith(LocalDateTime.now(), null);

        totals.addClosed(closed);

        assertThat(totals.closedCost).isEqualByComparingTo("400");
        assertThat(totals.closedExitValue).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_accumulateAcrossMultipleClosedLots_when_addClosedCalledRepeatedly() {
        SpotTotals totals = new SpotTotals();
        PortfolioPosition first = position(new BigDecimal("10"), new BigDecimal("40"));
        first.closeWith(LocalDateTime.now(), new BigDecimal("60"));
        PortfolioPosition second = position(new BigDecimal("2"), new BigDecimal("100"));
        second.closeWith(LocalDateTime.now(), new BigDecimal("150"));

        totals.addClosed(first);
        totals.addClosed(second);

        assertThat(totals.closedCost).isEqualByComparingTo("600");
        assertThat(totals.closedExitValue).isEqualByComparingTo("900");
    }

    @Test
    void should_valueAtLivePrice_when_addOpenGivenNonNullPrice() {
        SpotTotals totals = new SpotTotals();
        PortfolioPosition open = position(new BigDecimal("10"), new BigDecimal("50"));

        totals.addOpen(open, new BigDecimal("70"));

        assertThat(totals.openCost).isEqualByComparingTo("500");
        assertThat(totals.spotValue).isEqualByComparingTo("700");
        assertThat(totals.closedCost).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(totals.closedExitValue).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_treatEntryAsMarketValue_when_addOpenGivenNullPrice() {
        SpotTotals totals = new SpotTotals();
        PortfolioPosition open = position(new BigDecimal("10"), new BigDecimal("50"));

        totals.addOpen(open, null);

        assertThat(totals.openCost).isEqualByComparingTo("500");
        assertThat(totals.spotValue).isEqualByComparingTo("500");
    }

    @Test
    void should_accumulateAcrossMixedOpenLots_when_addOpenCalledWithAndWithoutPrice() {
        SpotTotals totals = new SpotTotals();
        PortfolioPosition priced = position(new BigDecimal("10"), new BigDecimal("50"));
        PortfolioPosition flat = position(new BigDecimal("4"), new BigDecimal("25"));

        totals.addOpen(priced, new BigDecimal("70"));
        totals.addOpen(flat, null);

        assertThat(totals.openCost).isEqualByComparingTo("600");
        assertThat(totals.spotValue).isEqualByComparingTo("800");
    }

    @Test
    void should_roundEveryBucketToPriceScaleHalfUp_when_scaleInvoked() {
        SpotTotals totals = new SpotTotals();
        // Inputs carry more decimals than the PRICE scale so scale() must round (HALF_UP) each bucket:
        // 3 × ...5 products land on a 9th decimal that rounds into the 8th.
        PortfolioPosition open = position(new BigDecimal("3"), new BigDecimal("0.333333335"));
        PortfolioPosition closed = position(new BigDecimal("3"), new BigDecimal("0.111111115"));
        closed.closeWith(LocalDateTime.now(), new BigDecimal("0.222222225"));
        totals.addOpen(open, new BigDecimal("0.444444445"));
        totals.addClosed(closed);

        totals.scale();

        // 3 × 0.444444445 = 1.333333335 → HALF_UP @8 → 1.33333334; likewise for the other buckets.
        assertThat(totals.spotValue).isEqualByComparingTo("1.33333334");
        assertThat(totals.openCost).isEqualByComparingTo("1.00000001");
        assertThat(totals.closedExitValue).isEqualByComparingTo("0.66666668");
        assertThat(totals.closedCost).isEqualByComparingTo("0.33333335");
        assertThat(totals.spotValue.scale()).isEqualTo(8);
        assertThat(totals.openCost.scale()).isEqualTo(8);
        assertThat(totals.closedExitValue.scale()).isEqualTo(8);
        assertThat(totals.closedCost.scale()).isEqualTo(8);
    }

    private PortfolioPosition position(BigDecimal quantity, BigDecimal entryPrice) {
        return PortfolioPosition.builder()
                .assetType(AssetType.STOCK)
                .assetCode("THYAO.IS")
                .quantity(quantity)
                .entryPrice(entryPrice)
                .entryDate(LocalDateTime.now())
                .build();
    }
}
