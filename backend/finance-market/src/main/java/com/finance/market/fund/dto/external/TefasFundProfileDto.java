package com.finance.market.fund.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * External TEFAS API payload describing a fund's static profile: identity (code,
 * name, ISIN, KAP link), tradability status, trading limits and commissions, daily
 * trade window, settlement valors (sell/buyback), interest content, and risk rating.
 * Turkish source field names are mapped to English accessors via {@link JsonProperty};
 * unknown fields are ignored so the contract tolerates upstream additions.
 */
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
