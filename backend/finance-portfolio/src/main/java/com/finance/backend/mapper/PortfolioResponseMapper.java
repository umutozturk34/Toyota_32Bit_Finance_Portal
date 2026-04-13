package com.finance.backend.mapper;

import com.finance.backend.dto.response.PortfolioResponse;
import com.finance.backend.dto.response.PositionResponse;
import com.finance.backend.dto.response.TransactionResponse;
import com.finance.backend.model.Portfolio;
import com.finance.backend.model.PortfolioPosition;
import com.finance.backend.model.PortfolioTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public abstract class PortfolioResponseMapper {

    @Mapping(target = "cashBalanceTry", source = "cashBalance")
    public abstract PortfolioResponse toPortfolioResponse(Portfolio portfolio, BigDecimal cashBalance);

    @Mapping(target = "assetType", expression = "java(txn.getAssetType().name())")
    @Mapping(target = "side", expression = "java(txn.getSide().name())")
    public abstract TransactionResponse toTransactionResponse(PortfolioTransaction txn);

    public abstract List<TransactionResponse> toTransactionResponses(List<PortfolioTransaction> txns);

    public PositionResponse toPositionResponse(PortfolioPosition pos,
                                                BigDecimal currentPriceTry,
                                                BigDecimal sellPriceTry,
                                                BigDecimal commissionRate,
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
                pos.getAverageCostTry(),
                pos.getTotalCostTry(),
                currentPriceTry,
                sellPriceTry,
                commissionRate,
                marketValueTry,
                pnlTry,
                pnlPercent
        );
    }
}
