package com.finance.portfolio.derivative.mapper;

import com.finance.market.viop.model.ViopCategory;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.portfolio.derivative.dto.response.DerivativePositionResponse;
import com.finance.portfolio.derivative.model.DerivativeCloseReason;
import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DerivativePositionMapperTest {

    private final DerivativePositionMapper mapper = new DerivativePositionMapper();

    private ViopContract contract(String symbol, BigDecimal contractSize, BigDecimal lastPrice) {
        return ViopContract.builder()
                .symbol(symbol)
                .kind(ViopContractKind.FUTURE)
                .category(ViopCategory.CURRENCY_FUTURE_TRY)
                .underlying("USDTRY")
                .expiryDate(LocalDate.of(2026, 6, 30))
                .contractSize(contractSize)
                .initialMargin(new BigDecimal("3500.00"))
                .currency("TRY")
                .lastPrice(lastPrice)
                .active(true)
                .build();
    }

    private DerivativePosition openPosition(ViopContract contract) {
        return DerivativePosition.builder()
                .id(10L)
                .direction(DerivativeDirection.LONG)
                .entryDate(LocalDate.of(2026, 4, 1))
                .entryPrice(new BigDecimal("35.20"))
                .quantityLot(new BigDecimal("2"))
                .viopContract(contract)
                .build();
    }

    @Test
    void should_mapAllContractAndPositionFields_when_positionIsOpen() {
        ViopContract c = contract("F_USDTRY0626", new BigDecimal("1000"), new BigDecimal("35.50"));
        DerivativePosition pos = openPosition(c);

        DerivativePositionResponse response = mapper.toResponse(pos);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.contractSymbol()).isEqualTo("F_USDTRY0626");
        assertThat(response.contractKind()).isEqualTo("FUTURE");
        assertThat(response.contractCategory()).isEqualTo("CURRENCY_FUTURE_TRY");
        assertThat(response.underlying()).isEqualTo("USDTRY");
        assertThat(response.expiryDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(response.contractSize()).isEqualByComparingTo("1000");
        assertThat(response.initialMargin()).isEqualByComparingTo("3500.00");
        assertThat(response.currency()).isEqualTo("TRY");
        assertThat(response.direction()).isEqualTo(DerivativeDirection.LONG);
        assertThat(response.entryPrice()).isEqualByComparingTo("35.20");
        assertThat(response.quantityLot()).isEqualByComparingTo("2");
        assertThat(response.currentPrice()).isEqualByComparingTo("35.50");
        assertThat(response.pnl()).isEqualByComparingTo("600");
        assertThat(response.nominalExposure()).isEqualByComparingTo("70400");
        assertThat(response.lockedMargin()).isEqualByComparingTo("7000.00");
        assertThat(response.open()).isTrue();
    }

    @Test
    void should_useClosePriceForPnl_when_positionIsClosed() {
        ViopContract c = contract("F_USDTRY0626", new BigDecimal("1000"), new BigDecimal("35.50"));
        DerivativePosition pos = openPosition(c);
        pos.closeWith(LocalDate.of(2026, 5, 1), new BigDecimal("36.00"), DerivativeCloseReason.USER_CLOSED);

        DerivativePositionResponse response = mapper.toResponse(pos);

        assertThat(response.open()).isFalse();
        assertThat(response.closePrice()).isEqualByComparingTo("36.00");
        assertThat(response.closeReason()).isEqualTo(DerivativeCloseReason.USER_CLOSED);
        assertThat(response.pnl()).isEqualByComparingTo("1600");
    }

    @Test
    void should_handleNullContract_when_positionLacksContractReference() {
        DerivativePosition pos = DerivativePosition.builder()
                .id(11L)
                .direction(DerivativeDirection.SHORT)
                .entryDate(LocalDate.of(2026, 4, 1))
                .entryPrice(new BigDecimal("100"))
                .quantityLot(new BigDecimal("1"))
                .build();

        DerivativePositionResponse response = mapper.toResponse(pos);

        assertThat(response.contractSymbol()).isNull();
        assertThat(response.contractKind()).isNull();
        assertThat(response.contractSize()).isNull();
        assertThat(response.currentPrice()).isNull();
    }

    @Test
    void should_mapMultiplePositions_when_toResponsesInvoked() {
        ViopContract c = contract("F_USDTRY0626", new BigDecimal("1000"), new BigDecimal("35.50"));
        List<DerivativePositionResponse> result = mapper.toResponses(List.of(openPosition(c), openPosition(c)));

        assertThat(result).hasSize(2);
    }
}
