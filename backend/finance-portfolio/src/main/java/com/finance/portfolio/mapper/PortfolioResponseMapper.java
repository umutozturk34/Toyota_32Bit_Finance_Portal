package com.finance.portfolio.mapper;

import com.finance.portfolio.dto.response.AllocationItem;
import com.finance.portfolio.dto.response.PortfolioResponse;
import com.finance.portfolio.dto.response.PortfolioSummaryResponse;
import com.finance.portfolio.dto.response.PositionResponse;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioPosition;
import org.mapstruct.Mapper;

import java.math.BigDecimal;

/**
 * MapStruct mapper assembling the portfolio API response DTOs (portfolio, position, summary,
 * allocation) from entities plus pre-computed valuation figures supplied by the services.
 */
@Mapper(componentModel = "spring")
public abstract class PortfolioResponseMapper {

    public abstract PortfolioResponse toPortfolioResponse(Portfolio portfolio);

    public PositionResponse toPositionResponse(PortfolioPosition pos,
                                                BigDecimal currentPriceTry,
                                                BigDecimal entryValueTry,
                                                BigDecimal marketValueTry,
                                                BigDecimal pnlTry,
                                                BigDecimal pnlPercent,
                                                BigDecimal realPnlPercent,
                                                String assetName,
                                                String assetImage) {
        return new PositionResponse(
                pos.getId(),
                pos.getAssetType().name(),
                pos.getAssetCode(),
                assetName,
                assetImage,
                pos.getQuantity(),
                pos.getEntryDate(),
                pos.getEntryPrice(),
                pos.getExitDate(),
                pos.getExitPrice(),
                pos.isClosed() ? pos.realizedPnl() : null,
                currentPriceTry,
                entryValueTry,
                marketValueTry,
                pnlTry,
                pnlPercent,
                realPnlPercent,
                null
        );
    }

    /** Minimal response for write endpoints (no live valuation): only entry value is populated, the rest zeroed. */
    public PositionResponse toPositionResponseShell(PortfolioPosition pos) {
        return toPositionResponse(pos,
                BigDecimal.ZERO, pos.entryValue(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null);
    }

    public PortfolioSummaryResponse toSummaryResponse(BigDecimal totalValueTry,
                                                      BigDecimal totalEntryValueTry,
                                                      BigDecimal totalPnlTry,
                                                      BigDecimal pnlPercent,
                                                      BigDecimal dailyPnlTry,
                                                      BigDecimal dailyPnlPercent,
                                                      BigDecimal realPnlTry,
                                                      BigDecimal realPnlPercent,
                                                      BigDecimal cpiGrowthPercent,
                                                      java.util.Map<String, com.finance.portfolio.dto.response.CurrencyFramePct> frames) {
        return new PortfolioSummaryResponse(
                totalValueTry, totalEntryValueTry, totalPnlTry, pnlPercent,
                dailyPnlTry, dailyPnlPercent,
                realPnlTry, realPnlPercent, cpiGrowthPercent,
                frames
        );
    }

    public AllocationItem toAllocationItem(String label,
                                           String assetType,
                                           BigDecimal valueTry,
                                           BigDecimal percent) {
        return new AllocationItem(label, assetType, valueTry, percent, null, null);
    }

    public AllocationItem toAllocationItem(String label,
                                           String assetType,
                                           BigDecimal valueTry,
                                           BigDecimal percent,
                                           BigDecimal costTry,
                                           BigDecimal realizedPnlTry) {
        return new AllocationItem(label, assetType, valueTry, percent, costTry, realizedPnlTry);
    }

    public AllocationItem toAllocationItem(String label,
                                           String assetType,
                                           BigDecimal valueTry,
                                           BigDecimal percent,
                                           BigDecimal costTry,
                                           BigDecimal realizedPnlTry,
                                           java.util.Map<String, BigDecimal> realizedPnlByCurrency) {
        return new AllocationItem(label, assetType, valueTry, percent, costTry, realizedPnlTry,
                realizedPnlByCurrency != null ? realizedPnlByCurrency : java.util.Map.of());
    }
}
