package com.finance.market.bank.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Raw row shape returned by {@code doviz.com}'s per-currency bank endpoint. Keys are best-effort
 * — Jackson ignores unknown fields and tolerates missing ones, so site-side renames degrade
 * gracefully (rate ends up null and the row is skipped).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DovizComBankRow(
        @JsonProperty("bank") String bankName,
        @JsonProperty("code") String bankCode,
        @JsonProperty("logo") String logo,
        @JsonProperty("buying") BigDecimal buying,
        @JsonProperty("selling") BigDecimal selling
) { }
