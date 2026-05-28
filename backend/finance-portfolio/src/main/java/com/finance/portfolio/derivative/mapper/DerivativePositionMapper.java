package com.finance.portfolio.derivative.mapper;

import com.finance.market.viop.model.ViopContract;
import com.finance.portfolio.derivative.dto.response.DerivativePositionResponse;
import com.finance.portfolio.derivative.model.DerivativePosition;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Maps a {@link DerivativePosition} to its dedicated API response, flattening contract fields and computing live PnL/exposure/margin. */
@Component
public class DerivativePositionMapper {

    /** Builds the response; PnL is realized when closed, else unrealized against the contract's last price. */
    public DerivativePositionResponse toResponse(DerivativePosition position) {
        ViopContract contract = position.getViopContract();
        BigDecimal currentPrice = contract != null ? contract.getLastPrice() : null;
        BigDecimal pnl = position.realizedOrUnrealizedPnl(currentPrice);
        return new DerivativePositionResponse(
                position.getId(),
                contract != null ? contract.getSymbol() : null,
                contract != null ? contract.resolveDisplayName() : null,
                contract != null && contract.getKind() != null ? contract.getKind().name() : null,
                contract != null && contract.getCategory() != null ? contract.getCategory().name() : null,
                contract != null ? contract.getUnderlying() : null,
                contract != null ? contract.getExpiryDate() : null,
                contract != null ? contract.getContractSize() : null,
                contract != null ? contract.getInitialMargin() : null,
                contract != null ? contract.resolvePriceCurrency() : null,
                position.getDirection(),
                position.getEntryDate(),
                position.getEntryPrice(),
                position.getQuantityLot(),
                position.getCloseDate(),
                position.getClosePrice(),
                position.getCloseReason(),
                currentPrice,
                pnl,
                position.nominalExposure(),
                position.lockedMargin(),
                position.isOpen(),
                position.getCreatedAt(),
                position.getUpdatedAt()
        );
    }

    public List<DerivativePositionResponse> toResponses(List<DerivativePosition> positions) {
        return positions.stream().map(this::toResponse).toList();
    }
}
