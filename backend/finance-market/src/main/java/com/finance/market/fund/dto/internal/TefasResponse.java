package com.finance.market.fund.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.finance.market.fund.dto.external.deserializer.SafeBigDecimalDeserializer;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TefasResponse(
        String errorCode,
        String errorMessage,
        List<FundData> resultList,
        Integer toplamSayi,
        Integer toplamSayfa
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FundData(
            String fonKodu,
            String fonUnvan,
            String tarih,
            @JsonDeserialize(using = SafeBigDecimalDeserializer.class) BigDecimal fiyat,
            @JsonDeserialize(using = SafeBigDecimalDeserializer.class) BigDecimal borsaBultenFiyat,
            @JsonDeserialize(using = SafeBigDecimalDeserializer.class) BigDecimal tedPaySayisi,
            @JsonDeserialize(using = SafeBigDecimalDeserializer.class) BigDecimal kisiSayisi,
            @JsonDeserialize(using = SafeBigDecimalDeserializer.class) BigDecimal portfoyBuyukluk,
            Integer rn
    ) {}
}
