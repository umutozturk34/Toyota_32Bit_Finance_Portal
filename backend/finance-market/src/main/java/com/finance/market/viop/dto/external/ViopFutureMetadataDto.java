package com.finance.market.viop.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw VadeliIslemler future metadata row (Turkish-named fields) for one listed future. */
public record ViopFutureMetadataDto(
        @JsonProperty("Title") String symbol,
        @JsonProperty("DAYANAK_VARLIK") String underlying,
        @JsonProperty("VADE_TARIHI") String expiryDate,
        @JsonProperty("SOZLESME_BUYUKLUGU") String contractSize,
        @JsonProperty("UZLASMA_TIPI") String settlementType,
        @JsonProperty("PARA_BIRIMI") String currency,
        @JsonProperty("BASLANGIC_TEMINATI") String initialMargin,
        @JsonProperty("SOZLESME_TURU") String contractType
) { }
