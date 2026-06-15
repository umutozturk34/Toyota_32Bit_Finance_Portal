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
    private BondLimits bondLimits = new BondLimits();
    private Deposit deposit = new Deposit();
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
        // Cap on a single lot's TRY value (price × quantity). The per-field price/quantity caps alone allow a
        // product up to 1e18, but the daily-snapshot money columns are numeric(23,8) (max < 1e15) — so an
        // absurd lot overflows the snapshot INSERT, aborts the whole batch, and silently breaks the portfolio's
        // entire chart. 1e13 (10 trillion TRY) is far above any real holding yet well under the column limit.
        private BigDecimal maxLotValueTry = new BigDecimal("10000000000000");
    }

    @Getter
    @Setter
    public static class BondLimits {
        // Earliest bond holding entry date: the first trading day of 2000, the floor of the scraped
        // bond/FX history a hypothetical lot can be valued against. Mirrors LotLimits.minEntryDate.
        private LocalDate minEntryDate = LocalDate.of(2000, 1, 4);
        // Nominal amount held. Bonds are typically traded in large nominal blocks, so the cap is generous
        // while the price × quantity product stays well under the numeric(23,8) snapshot money columns.
        private BigDecimal minQuantity = new BigDecimal("0.00000001");
        private BigDecimal maxQuantity = new BigDecimal("1000000000");
        // Clean price quoted in TRY per 100 nominal. Real quotes orbit 100; the cap is intentionally
        // loose to tolerate deep-discount/long-history quotes without blocking a legitimate add.
        private BigDecimal minPriceTry = new BigDecimal("0.0001");
        private BigDecimal maxPriceTry = new BigDecimal("100000");
        // User-editable coupon override (SEMI-ANNUAL percent). The published bond coupon is only a
        // suggestion; the user may overwrite it. Optional — null tracks the published rate. Bounded so a
        // mistyped value (e.g. a price pasted into the coupon field) cannot persist an absurd display rate;
        // 100% per half-year is far above any real Türkiye Hazine coupon yet generous for hyperinflation eras.
        private BigDecimal minCouponRate = BigDecimal.ZERO;
        private BigDecimal maxCouponRate = new BigDecimal("100");
    }

    @Getter
    @Setter
    public static class Deposit {
        // Withholding tax (stopaj) on Türkiye time-deposit interest, deducted at payout. A single representative
        // rate (real TR rates tier by maturity, currently ~15%); applied to the gross interest only, never the
        // principal, so the holder sees the net realizable value the way the bank pays it out.
        private BigDecimal withholdingTaxRate = new BigDecimal("0.15");
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
