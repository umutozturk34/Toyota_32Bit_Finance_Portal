package com.finance.market.viop.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ViopOptionMetadataDto(
        @JsonProperty("Title") String underlying,
        @JsonProperty("GRUP") String group,
        @JsonProperty("ORNEKURUNKODU") String sampleCode,
        @JsonProperty("SOZLESMEBUYUKLUGU") String contractSize,
        @JsonProperty("ISLEMSAATLERI") String tradingHours,
        @JsonProperty("PARABIRIMI") String currency,
        @JsonProperty("DAYANAKVARLIK") String underlyingDisplay,
        @JsonProperty("DayanakKod") String underlyingCode,
        @JsonProperty("DayanakGrup") String underlyingGroup,
        @JsonProperty("UZLASMASEKLI") String settlementMethod,
        @JsonProperty("OPSIYONTURU") String optionType
) { }
