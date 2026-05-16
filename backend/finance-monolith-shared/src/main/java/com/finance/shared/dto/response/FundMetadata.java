package com.finance.shared.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record FundMetadata(
        String fundType,
        BigDecimal portfolioSize,
        BigDecimal investorCount,
        BigDecimal bulletinPrice,
        BigDecimal shareCount,
        Integer riskValue,
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
        List<AllocationEntry> allocations
) implements MarketAssetMetadata {

    public record AllocationEntry(String assetClass, BigDecimal percentage) {}
}
