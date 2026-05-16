package com.finance.market.viop.mapper;

import com.finance.market.viop.dto.ViopContractSpec;
import com.finance.market.viop.dto.external.ViopFutureMetadataDto;
import com.finance.market.viop.dto.external.ViopOptionMetadataDto;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.market.viop.model.ViopOptionSide;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ViopMetadataMapperTest {

    private final ViopMetadataMapper mapper = new ViopMetadataMapper();

    private ViopFutureMetadataDto futureDto() {
        return new ViopFutureMetadataDto(
                "F_USDTRY0626",
                "USDTRY",
                "30.06.2026 17:45:00",
                "1.000",
                "Nakdi",
                "TRY",
                "3.500,75",
                "Vadeli");
    }

    private ViopOptionMetadataDto optionDto() {
        return new ViopOptionMetadataDto(
                "AKBNK",
                "PAY",
                "O_AKBNKE0526P45.00",
                "100 hisse",
                "10:00-18:00",
                "TRY",
                "Akbank T.A.Ş.",
                "AKBNK",
                "PAY",
                "Nakdi",
                "PUT");
    }

    @Test
    void should_mapFutureDtoIntoSpec_when_allFieldsProvided() {
        ViopContractSpec spec = mapper.toFutureSpec(futureDto());

        assertThat(spec.symbol()).isEqualTo("F_USDTRY0626");
        assertThat(spec.kind()).isEqualTo(ViopContractKind.FUTURE);
        assertThat(spec.underlying()).isEqualTo("USDTRY");
        assertThat(spec.expiryDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(spec.contractSize()).isEqualByComparingTo("1000");
        assertThat(spec.initialMargin()).isEqualByComparingTo("3500.75");
        assertThat(spec.settlementType()).isEqualTo("Nakdi");
        assertThat(spec.currency()).isEqualTo("TRY");
        assertThat(spec.displayName()).startsWith("USDTRY");
    }

    @Test
    void should_returnNullExpiry_when_dateStringMalformed() {
        ViopFutureMetadataDto dto = new ViopFutureMetadataDto(
                "F_USDTRY0626", "USDTRY", "not-a-date", "1.000", "Nakdi", "TRY", "3.500,75", "Vadeli");

        ViopContractSpec spec = mapper.toFutureSpec(dto);

        assertThat(spec.expiryDate()).isNull();
    }

    @Test
    void should_returnNullDecimal_when_marginStringMalformed() {
        ViopFutureMetadataDto dto = new ViopFutureMetadataDto(
                "F_USDTRY0626", "USDTRY", "30.06.2026 17:45:00", "1.000", "Nakdi", "TRY", "abc", "Vadeli");

        ViopContractSpec spec = mapper.toFutureSpec(dto);

        assertThat(spec.initialMargin()).isNull();
    }

    @Test
    void should_mapOptionDtoIntoTemplateSpec_when_allFieldsProvided() {
        ViopContractSpec spec = mapper.toOptionTemplateSpec(optionDto());

        assertThat(spec.kind()).isEqualTo(ViopContractKind.OPTION);
        assertThat(spec.symbol()).isEqualTo("O_AKBNKE0526P45.00");
        assertThat(spec.displayName()).isEqualTo("Akbank T.A.Ş.");
        assertThat(spec.underlying()).isEqualTo("AKBNK");
        assertThat(spec.contractSize()).isEqualByComparingTo("100");
        assertThat(spec.settlementType()).isEqualTo("Nakdi");
        assertThat(spec.currency()).isEqualTo("TRY");
        assertThat(spec.optionSide()).isEqualTo(ViopOptionSide.PUT);
    }

    @Test
    void should_fallBackToUnderlyingTitle_when_optionUnderlyingCodeBlank() {
        ViopOptionMetadataDto dto = new ViopOptionMetadataDto(
                "AKBNK", "PAY", "O_AKBNKE0526P45.00", "100 hisse", "10:00-18:00", "TRY",
                "Akbank T.A.Ş.", "", "PAY", "Nakdi", "CALL");

        ViopContractSpec spec = mapper.toOptionTemplateSpec(dto);

        assertThat(spec.underlying()).isEqualTo("AKBNK");
        assertThat(spec.optionSide()).isEqualTo(ViopOptionSide.CALL);
    }

    @Test
    void should_parseSizeFromMixedDescription_when_descriptionHasUnitsAndDigits() {
        ViopOptionMetadataDto dto = new ViopOptionMetadataDto(
                "AKBNK", "PAY", "O_AKBNKE0526P45.00", "1.000 adet hisse", "10:00-18:00", "TRY",
                "Akbank", "AKBNK", "PAY", "Nakdi", "PUT");

        ViopContractSpec spec = mapper.toOptionTemplateSpec(dto);

        assertThat(spec.contractSize()).isEqualByComparingTo("1000");
    }
}
