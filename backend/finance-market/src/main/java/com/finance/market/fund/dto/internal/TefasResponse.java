package com.finance.market.fund.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.annotation.JsonDeserialize;
import com.finance.market.fund.dto.external.deserializer.SafeBigDecimalDeserializer;

import java.math.BigDecimal;
import java.util.List;

/**
 * Envelope for the TEFAS time-series response: an optional error code/message, the list
 * of per-day {@link FundData} rows, and total record/page counts for pagination. An
 * absent {@code resultList} alongside a populated {@code errorMessage} signals an upstream
 * failure; numeric fields use a fault-tolerant deserializer to absorb malformed values.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TefasResponse(
        String errorCode,
        String errorMessage,
        List<FundData> resultList,
        Integer toplamSayi,
        Integer toplamSayfa
) {
    /**
     * A single daily observation for a fund: code and name, the date, unit price and
     * exchange-bulletin price, outstanding share count, investor count, portfolio size,
     * and row number. Monetary/count fields use {@link SafeBigDecimalDeserializer} so
     * blank or non-numeric upstream values degrade gracefully instead of failing parsing.
     */
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
