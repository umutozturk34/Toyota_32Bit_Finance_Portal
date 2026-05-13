package com.finance.portfolio.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.portfolio")
public class PortfolioProperties {

    private String defaultName = "Ana Portföy";
    private String defaultCurrency = "TRY";
    private BigDecimal initialBalance = new BigDecimal("1000000.0000");
    private BigDecimal minTransactionAmountTry = new BigDecimal("10");
    private int historicalRateLookbackDays = 7;
    private LotLimits lotLimits = new LotLimits();
    private Snapshot snapshot = new Snapshot();
    private Backfill backfill = new Backfill();

    @Getter
    @Setter
    public static class LotLimits {
        private LocalDate minEntryDate = LocalDate.of(1992, 1, 1);
        private BigDecimal minPriceTry = new BigDecimal("0.0001");
        private BigDecimal maxPriceTry = new BigDecimal("1000000000");
        private BigDecimal minQuantity = new BigDecimal("0.00000001");
        private BigDecimal maxQuantity = new BigDecimal("1000000000");
    }

    @Getter
    @Setter
    public static class Snapshot {
        private int dailyLookbackHours = 24;
    }

    @Getter
    @Setter
    public static class Backfill {
        private int priceLookbackDays = 7;
        private int lockStripes = 32;
    }
}
