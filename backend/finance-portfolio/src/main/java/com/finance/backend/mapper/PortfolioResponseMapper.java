package com.finance.backend.mapper;

import com.finance.backend.dto.response.PortfolioResponse;
import com.finance.backend.dto.response.PositionResponse;
import com.finance.backend.model.Portfolio;
import com.finance.backend.model.PortfolioPosition;
import org.mapstruct.Mapper;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public abstract class PortfolioResponseMapper {

    public abstract PortfolioResponse toPortfolioResponse(Portfolio portfolio);

    public PositionResponse toPositionResponse(PortfolioPosition pos,
                                                BigDecimal currentPriceTry,
                                                BigDecimal entryValueTry,
                                                BigDecimal marketValueTry,
                                                BigDecimal pnlTry,
                                                BigDecimal pnlPercent,
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
                currentPriceTry,
                entryValueTry,
                marketValueTry,
                pnlTry,
                pnlPercent
        );
    }

    public PositionResponse toPositionResponseShell(PortfolioPosition pos) {
        return toPositionResponse(pos,
                BigDecimal.ZERO, pos.entryValue(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null);
    }
}
