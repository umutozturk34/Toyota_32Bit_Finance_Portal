package com.finance.market.fund.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TefasFundInfoDto(
        @JsonProperty("fonKodu") String fundCode,
        @JsonProperty("fonUnvan") String name,
        @JsonProperty("sonFiyat") BigDecimal lastPrice,
        @JsonProperty("gunlukGetiri") BigDecimal dailyReturn,
        @JsonProperty("payAdet") BigDecimal shareCount,
        @JsonProperty("portBuyukluk") BigDecimal portfolioSize,
        @JsonProperty("fonKategori") String category,
        @JsonProperty("kategoriDerece") Integer categoryRank,
        @JsonProperty("kategoriFonSay") Integer categoryFundCount,
        @JsonProperty("yatirimciSayi") BigDecimal investorCount,
        @JsonProperty("pazarPayi") BigDecimal marketShare
) {}
