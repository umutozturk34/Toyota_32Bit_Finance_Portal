package com.finance.market.bank.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DovizComBankRow(
        @JsonProperty("bank") String bankName,
        @JsonProperty("code") String bankCode,
        @JsonProperty("logo") String logo,
        @JsonProperty("buying") BigDecimal buying,
        @JsonProperty("selling") BigDecimal selling
) { }
