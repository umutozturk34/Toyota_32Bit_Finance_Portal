package com.finance.portfolio.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Externalized portfolio tuning under {@code app.portfolio}: per-user caps, lot input limits, and snapshot/backfill/view/performance knobs. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.portfolio")
public class PortfolioProperties {

    private String defaultName = "Ana Portföy";
    private String defaultCurrency = "TRY";
    private BigDecimal initialBalance = new BigDecimal("1000000.0000");
    private BigDecimal minTransactionAmountTry = new BigDecimal("10");
    private int historicalRateLookbackDays = 30;
    private int maxPortfoliosPerUser = 5;
    private LotLimits lotLimits = new LotLimits();
    private Snapshot snapshot = new Snapshot();
    private Backfill backfill = new Backfill();
    private View view = new View();
    private Performance performance = new Performance();

    @Getter
    @Setter
    public static class LotLimits {
        // Earliest lot entry date: the first trading day of 2000, the floor of the EUR/TRY FX history a
        // multi-currency lot must be valuable at. Anything older has no EUR rate and renders as broken K/Z.
        private LocalDate minEntryDate = LocalDate.of(2000, 1, 4);
        private BigDecimal minPriceTry = new BigDecimal("0.0001");
        private BigDecimal maxPriceTry = new BigDecimal("1000000000");
        private BigDecimal minQuantity = new BigDecimal("0.000001");
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
        private int stateCacheExpiryHours = 2;
        private int stateCacheMaxSize = 1_000;
        private int emitterCacheExpiryMinutes = 30;
        private int emittersCacheMaxSize = 10_000;
        private int snapshotTimeHour = 12;
    }

    @Getter
    @Setter
    public static class View {
        private int positionPageSize = 10;
    }

    @Getter
    @Setter
    public static class Performance {
        private int detailTopNLimit = 8;
    }
}
