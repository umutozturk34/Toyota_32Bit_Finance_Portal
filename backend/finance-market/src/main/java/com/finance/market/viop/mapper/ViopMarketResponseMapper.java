package com.finance.market.viop.mapper;

import com.finance.common.model.MarketType;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.viop.model.ViopContract;
import com.finance.shared.dto.response.ViopMetadata;
import org.springframework.stereotype.Component;

import java.util.List;

/** Maps {@link ViopContract} entities to API {@link MarketAssetResponse}s with VIOP-specific metadata. */
@Component
public class ViopMarketResponseMapper {

    public MarketAssetResponse toResponse(ViopContract contract) {
        return new MarketAssetResponse(
                contract.getSymbol(),
                contract.resolveDisplayName(),
                contract.getImage(),
                MarketType.VIOP,
                contract.getPriceTry(),
                contract.getChangeAmount(),
                contract.getChangePercent(),
                contract.getLastUpdated(),
                buildMetadata(contract)
        );
    }

    public List<MarketAssetResponse> toResponses(List<ViopContract> contracts) {
        return contracts.stream().map(this::toResponse).toList();
    }

    private ViopMetadata buildMetadata(ViopContract c) {
        return new ViopMetadata(
                c.getKind() != null ? c.getKind().name() : null,
                c.getCategory() != null ? c.getCategory().name() : null,
                c.getUnderlying(),
                c.getExpiryDate(),
                c.getContractSize(),
                c.getInitialMargin(),
                c.getSettlementType(),
                c.getCurrency(),
                c.getOptionSide() != null ? c.getOptionSide().name() : null,
                c.getStrikePrice(),
                c.getExerciseStyle() != null ? c.getExerciseStyle().name() : null,
                c.getVolumeLot(),
                c.getBid(),
                c.getAsk()
        );
    }
}
