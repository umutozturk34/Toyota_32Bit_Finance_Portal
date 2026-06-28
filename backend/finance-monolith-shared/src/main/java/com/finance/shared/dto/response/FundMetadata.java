package com.finance.shared.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fund-specific {@link MarketAssetMetadata}: classification, size/investor stats, category ranking,
 * trailing returns across windows, and asset-class allocation breakdown.
 */
public record FundMetadata(
        String fundType,
        BigDecimal portfolioSize,
        BigDecimal investorCount,
        BigDecimal bulletinPrice,
        BigDecimal shareCount,
        Integer riskValue,
        Integer sellValor,
        Integer buybackValor,
        String tradeStartTime,
        String tradeEndTime,
        String category,
        String subCategory,
        Integer categoryRank,
        Integer categoryTotalFunds,
        BigDecimal marketShare,
        BigDecimal return1m,
        BigDecimal return3m,
        BigDecimal return6m,
        BigDecimal return1y,
        BigDecimal returnYtd,
        BigDecimal return3y,
        BigDecimal return5y,
        String isinCode,
        String kapLink,
        List<AllocationEntry> allocations
) implements MarketAssetMetadata {

    /** One slice of a fund's portfolio: an asset class and its weight in percent. */
    public record AllocationEntry(String assetClass, BigDecimal percentage) {}
}
