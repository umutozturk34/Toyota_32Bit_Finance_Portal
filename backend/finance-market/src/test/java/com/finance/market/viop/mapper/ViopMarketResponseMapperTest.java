package com.finance.market.viop.mapper;

import com.finance.common.model.MarketType;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.viop.model.ViopCategory;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.market.viop.model.ViopOptionSide;
import com.finance.shared.dto.response.ViopMetadata;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ViopMarketResponseMapperTest {

    private final ViopMarketResponseMapper mapper = new ViopMarketResponseMapper();

    private ViopContract sampleContract() {
        return ViopContract.builder()
                .symbol("O_AKBNKE0526P45.00")
                .displayName("AKBNK Put 45 · 31 May 26")
                .kind(ViopContractKind.OPTION)
                .category(ViopCategory.PAY_OPTION)
                .underlying("AKBNK")
                .expiryDate(LocalDate.of(2026, 5, 31))
                .contractSize(new BigDecimal("100"))
                .initialMargin(new BigDecimal("250.00"))
                .settlementType("Nakdi")
                .currency("TRY")
                .optionSide(ViopOptionSide.PUT)
                .strikePrice(new BigDecimal("45.00"))
                .lastPrice(new BigDecimal("2.40"))
                .volumeLot(new BigDecimal("30"))
                .bid(new BigDecimal("2.35"))
                .ask(new BigDecimal("2.45"))
                .active(true)
                .build();
    }

    @Test
    void should_mapAllContractFieldsToResponse_when_contractIsFullyPopulated() {
        ViopContract contract = sampleContract();

        MarketAssetResponse response = mapper.toResponse(contract);

        assertThat(response.code()).isEqualTo("O_AKBNKE0526P45.00");
        assertThat(response.name()).isEqualTo("AKBNK Put 45 · 31 May 26");
        assertThat(response.type()).isEqualTo(MarketType.VIOP);
        assertThat(response.price()).isEqualByComparingTo("2.40");
    }

    @Test
    void should_populateAllMetadataFields_when_contractHasOptionalFields() {
        ViopContract contract = sampleContract();

        MarketAssetResponse response = mapper.toResponse(contract);
        ViopMetadata meta = (ViopMetadata) response.metadata();

        assertThat(meta.kind()).isEqualTo("OPTION");
        assertThat(meta.category()).isEqualTo("PAY_OPTION");
        assertThat(meta.underlying()).isEqualTo("AKBNK");
        assertThat(meta.expiryDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(meta.contractSize()).isEqualByComparingTo("100");
        assertThat(meta.initialMargin()).isEqualByComparingTo("250.00");
        assertThat(meta.settlementType()).isEqualTo("Nakdi");
        assertThat(meta.currency()).isEqualTo("TRY");
        assertThat(meta.optionSide()).isEqualTo("PUT");
        assertThat(meta.strikePrice()).isEqualByComparingTo("45.00");
        assertThat(meta.volumeLot()).isEqualByComparingTo("30");
        assertThat(meta.bid()).isEqualByComparingTo("2.35");
        assertThat(meta.ask()).isEqualByComparingTo("2.45");
    }

    @Test
    void should_fallBackToSymbol_when_displayNameAndNameAreBlank() {
        ViopContract contract = ViopContract.builder()
                .symbol("F_USDTRY0626")
                .kind(ViopContractKind.FUTURE)
                .underlying("USDTRY")
                .active(true)
                .build();

        MarketAssetResponse response = mapper.toResponse(contract);

        assertThat(response.name()).isEqualTo("F_USDTRY0626");
    }

    @Test
    void should_handleNullableEnumFields_when_kindOrCategoryMissing() {
        ViopContract contract = ViopContract.builder()
                .symbol("F_X").active(true).build();

        MarketAssetResponse response = mapper.toResponse(contract);
        ViopMetadata meta = (ViopMetadata) response.metadata();

        assertThat(meta.kind()).isNull();
        assertThat(meta.category()).isNull();
        assertThat(meta.optionSide()).isNull();
        assertThat(meta.exerciseStyle()).isNull();
    }

    @Test
    void should_mapMultipleContracts_when_toResponsesInvoked() {
        List<MarketAssetResponse> result = mapper.toResponses(List.of(sampleContract(), sampleContract()));

        assertThat(result).hasSize(2);
    }
}
