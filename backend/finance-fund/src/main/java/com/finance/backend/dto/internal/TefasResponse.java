package com.finance.backend.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.finance.backend.dto.external.deserializer.SafeBigDecimalDeserializer;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TefasResponse(
        int draw,
        int recordsTotal,
        int recordsFiltered,
        List<FundData> data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FundData(
            @JsonProperty("TARIH") String tarih,
            @JsonProperty("FONKODU") String fonKodu,
            @JsonProperty("FONUNVAN") String fonUnvan,
            @JsonProperty("BORSABULTENFIYAT") @JsonDeserialize(using = SafeBigDecimalDeserializer.class) BigDecimal borsaBultenFiyat,
            @JsonProperty("TEDPAYSAYISI") @JsonDeserialize(using = SafeBigDecimalDeserializer.class) BigDecimal tedPaySayisi,
            @JsonProperty("KISISAYISI") @JsonDeserialize(using = SafeBigDecimalDeserializer.class) BigDecimal kisiSayisi,
            @JsonProperty("PORTFOYBUYUKLUK") @JsonDeserialize(using = SafeBigDecimalDeserializer.class) BigDecimal portfolyoBuyukluk,
            @JsonProperty("FIYAT") @JsonDeserialize(using = SafeBigDecimalDeserializer.class) BigDecimal fiyat
    ) {}
}
