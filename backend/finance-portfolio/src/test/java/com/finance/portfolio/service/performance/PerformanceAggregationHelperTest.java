package com.finance.portfolio.service.performance;

import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.dto.response.PerformanceAssetDetail;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that a VALUE-LESS VIOP close-day row (quantity 0, carrying only dailyPnlTry) never renders as a "+0"
 * contributor in the K/Z Katkısı breakdown — neither in the by-code nor the by-type aggregation.
 */
class PerformanceAggregationHelperTest {

    private final PerformanceAggregationHelper helper = new PerformanceAggregationHelper(new PortfolioProperties());

    private static PortfolioAssetDailySnapshot row(AssetType type, String code, BigDecimal qty,
                                                   BigDecimal marketValue, BigDecimal pnl) {
        return PortfolioAssetDailySnapshot.builder()
                .assetType(type).assetCode(code)
                .quantity(qty)
                .unitPriceTry(BigDecimal.ONE)
                .marketValueTry(marketValue)
                .totalCostTry(marketValue.subtract(pnl))
                .pnlTry(pnl)
                .createdAt(LocalDateTime.of(2026, 6, 6, 18, 0))
                .build();
    }

    @Test
    void aggregateByCode_excludesValuelessViopCloseDayRow_fromDetails() {
        PortfolioAssetDailySnapshot openStock = row(AssetType.STOCK, "AKBNK",
                new BigDecimal("10"), new BigDecimal("1000"), new BigDecimal("100"));
        PortfolioAssetDailySnapshot viopClose = row(AssetType.VIOP, "F_XAUUSD0826",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        PerformanceAggregationHelper.AssetCodeAgg agg =
                helper.aggregateByCode(List.of(openStock, viopClose), new LinkedHashMap<>());

        assertThat(agg.details()).extracting(PerformanceAssetDetail::label)
                .contains("AKBNK").doesNotContain("F_XAUUSD0826");
        // Totals are unaffected — the value-less row contributes 0 either way.
        assertThat(agg.totalValue()).isEqualByComparingTo(new BigDecimal("1000"));
    }

    @Test
    void aggregateByType_excludesValuelessViopCloseDayRow_fromTypeContributors() {
        PortfolioAssetDailySnapshot openStock = row(AssetType.STOCK, "AKBNK",
                new BigDecimal("10"), new BigDecimal("1000"), new BigDecimal("100"));
        PortfolioAssetDailySnapshot viopClose = row(AssetType.VIOP, "F_XAUUSD0826",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        List<PerformanceAssetDetail> details =
                helper.aggregateByType(List.of(openStock, viopClose), new LinkedHashMap<>());

        // No "+0" VIOP type contributor when the only VIOP row is a value-less close-day marker.
        assertThat(details).extracting(PerformanceAssetDetail::label).contains("STOCK").doesNotContain("VIOP");
    }
}
