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

    /** Maps a portfolio entity to its response DTO (metadata only; positions/valuation added separately). */
    public abstract PortfolioResponse toPortfolioResponse(Portfolio portfolio);

    /**
     * Assembles a full position response by combining the persisted lot ({@code pos}) with
     * service-computed TRY valuation figures (current price, entry/market value, P&amp;L absolute and
     * percent, real/inflation-adjusted percent) and resolved display data (asset name and image).
     * Realized P&amp;L is populated only for closed positions; the trailing currency-frame field is left null.
     */
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

    /**
     * Assembles the portfolio summary response from pre-aggregated TRY totals: overall value/cost
     * and P&amp;L, the daily delta, realized P&amp;L, inflation (CPI) growth, realized cash, and the
     * per-currency K/Z frames keyed by currency code. Performs no computation, only field assembly.
     */
    public PortfolioSummaryResponse toSummaryResponse(BigDecimal totalValueTry,
                                                      BigDecimal totalEntryValueTry,
                                                      BigDecimal totalPnlTry,
                                                      BigDecimal pnlPercent,
                                                      BigDecimal dailyPnlTry,
                                                      BigDecimal dailyPnlPercent,
                                                      BigDecimal realPnlTry,
                                                      BigDecimal realPnlPercent,
                                                      BigDecimal cpiGrowthPercent,
                                                      BigDecimal realizedCashTry,
                                                      java.util.Map<String, com.finance.portfolio.dto.response.CurrencyFramePct> frames) {
        return new PortfolioSummaryResponse(
                totalValueTry, totalEntryValueTry, totalPnlTry, pnlPercent,
                dailyPnlTry, dailyPnlPercent,
                realPnlTry, realPnlPercent, cpiGrowthPercent,
                realizedCashTry,
                frames
        );
    }

    /** Builds an allocation slice (label, asset type, TRY value/percent, cost and realized P&amp;L) without per-currency breakdowns. */
    public AllocationItem toAllocationItem(String label,
                                           String assetType,
                                           BigDecimal valueTry,
                                           BigDecimal percent,
                                           BigDecimal costTry,
                                           BigDecimal realizedPnlTry) {
        return new AllocationItem(label, assetType, valueTry, percent, costTry, realizedPnlTry);
    }

    /**
     * Builds an allocation slice with the additional per-currency realized-P&amp;L and cost maps;
     * null maps are normalized to empty so the response never carries null collections.
     */
    public AllocationItem toAllocationItem(String label,
                                           String assetType,
                                           BigDecimal valueTry,
                                           BigDecimal percent,
                                           BigDecimal costTry,
                                           BigDecimal realizedPnlTry,
                                           java.util.Map<String, BigDecimal> realizedPnlByCurrency,
                                           java.util.Map<String, BigDecimal> costByCurrency) {
        return new AllocationItem(label, assetType, valueTry, percent, costTry, realizedPnlTry,
                realizedPnlByCurrency != null ? realizedPnlByCurrency : java.util.Map.of(),
                costByCurrency != null ? costByCurrency : java.util.Map.of());
    }
}
