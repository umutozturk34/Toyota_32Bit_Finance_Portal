package com.finance.market.fund.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TefasFundProfileDto(
        @JsonProperty("fonKodu") String fundCode,
        @JsonProperty("fonUnvan") String name,
        @JsonProperty("isinKodu") String isinCode,
        @JsonProperty("kapLink") String kapLink,
        @JsonProperty("tefasDurum") String tefasStatus,
        @JsonProperty("minAlis") BigDecimal minBuy,
        @JsonProperty("minSatis") BigDecimal minSell,
        @JsonProperty("maxAlis") BigDecimal maxBuy,
        @JsonProperty("maxSatis") BigDecimal maxSell,
        @JsonProperty("girisKomisyonu") BigDecimal entryCommission,
        @JsonProperty("cikisKomisyonu") BigDecimal exitCommission,
        @JsonProperty("basIsSaat") String tradeStartTime,
        @JsonProperty("sonIsSaat") String tradeEndTime,
        @JsonProperty("fonSatisValor") Integer sellValor,
        @JsonProperty("fonGeriAlisValor") Integer buybackValor,
        @JsonProperty("faizIcerigi") String interestContent,
        @JsonProperty("riskDegeri") String riskValue
) {}
