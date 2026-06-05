package com.finance.portfolio.service.performance;

import com.finance.portfolio.dto.response.PerformancePoint;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioPerformanceServicePointTest {

    private static final LocalDateTime TS = LocalDateTime.of(2026, 5, 1, 0, 0);

    @ParameterizedTest
    @CsvSource({
            // openMv, openPnl, realized, closedCost, expValue, expCash, expTotalPnl, expPct
            "800,  200,  0,    0,    800,  0,    200,  33.3333",   // all open: held value 800
            "0,    0,    250,  1000, 0,    250,  250,  25.0000",   // all closed: nothing held now → value 0
            "800,  200,  250,  1000, 800,  250,  450,  28.1250",   // mixed: held value 800, P&L incl realized
            "500,  -100, -50,  400,  500,  -50,  -150, -15.0000",  // losses: held value 500
            "0,    0,    0,    0,    0,    0,    0,    0",          // empty
    })
    void should_foldClosedIntoTypePoint_when_assembled(String openMv, String openPnl, String realized,
                                                       String closedCost, String expValue, String expCash,
                                                       String expTotalPnl, String expPct) {
        PerformancePoint p = AggregatePerformanceBuilder.assembleTypePoint(
                TS, new BigDecimal(openMv), new BigDecimal(openMv), new BigDecimal(openPnl),
                new BigDecimal(realized), new BigDecimal(closedCost), List.of(), List.of(),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of());

        assertThat(p.totalValueTry()).isEqualByComparingTo(expValue);
        assertThat(p.cashTry()).isEqualByComparingTo(expCash);
        assertThat(p.totalPnlTry()).isEqualByComparingTo(expTotalPnl);
        assertThat(p.pnlPercent()).isEqualByComparingTo(expPct);
        // Open = total - closed must reproduce the open unrealized PnL that was fed in.
        assertThat(p.openPnlTry()).isEqualByComparingTo(openPnl);
    }
}
