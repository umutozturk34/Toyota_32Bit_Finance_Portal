package com.finance.market.fund.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TefasFundReturnsDto(
        @JsonProperty("fonKodu") String fundCode,
        @JsonProperty("fonUnvan") String name,
        @JsonProperty("fonTurAciklama") String subCategory,
        @JsonProperty("tefasDurum") Boolean tradable,
        @JsonProperty("getiri1a") BigDecimal return1m,
        @JsonProperty("getiri3a") BigDecimal return3m,
        @JsonProperty("getiri6a") BigDecimal return6m,
        @JsonProperty("getiri1y") BigDecimal return1y,
        @JsonProperty("getiriyb") BigDecimal returnYtd,
        @JsonProperty("getiri3y") BigDecimal return3y,
        @JsonProperty("getiri5y") BigDecimal return5y,
        @JsonProperty("riskDegeri") String riskValue
) {}
