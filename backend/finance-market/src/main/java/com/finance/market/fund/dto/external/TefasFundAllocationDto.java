package com.finance.market.fund.dto.external;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Inbound deserialization target for a TEFAS fund asset-allocation record. Beyond the fixed
 * identifying fields ({@code fonKodu}, {@code fonUnvan}, {@code tarih}), TEFAS returns one
 * numeric column per allocation category with provider-defined, variable keys; those dynamic
 * columns are collected into {@link #allocations()} via a {@link JsonAnySetter} catch-all rather
 * than declared statically, and unknown structural fields are otherwise ignored. Each allocation
 * value is the percentage weight of that category in the fund's portfolio.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TefasFundAllocationDto {

    @JsonProperty("fonKodu") private String fundCode;
    @JsonProperty("fonUnvan") private String name;
    @JsonProperty("tarih") private String date;
    private final Map<String, BigDecimal> allocations = new LinkedHashMap<>();

    /**
     * Jackson catch-all that captures every JSON property without an explicit mapping and
     * funnels the genuine allocation categories into {@link #allocations()}. It deliberately
     * filters out {@code null} values, the already-mapped identity columns
     * ({@code fonKodu}, {@code fonUnvan}, {@code tarih}) and the unrelated {@code bilFiyat}
     * field, accepts only {@link Number} values, and skips zero-valued weights so the resulting
     * map contains only categories with a non-zero allocation.
     *
     * @param key   the JSON property name (an allocation category code)
     * @param value the raw JSON value; retained only when it is a non-zero number
     */
    @JsonAnySetter
    public void putUnknown(String key, Object value) {
        if (value == null) return;
        if ("fonKodu".equals(key) || "fonUnvan".equals(key) || "tarih".equals(key) || "bilFiyat".equals(key)) return;
        if (value instanceof Number number) {
            BigDecimal decimal = new BigDecimal(number.toString());
            if (decimal.signum() != 0) allocations.put(key, decimal);
        }
    }

    /** @return the TEFAS fund code ({@code fonKodu}). */
    public String fundCode() { return fundCode; }

    /** @return the fund title ({@code fonUnvan}). */
    public String name() { return name; }

    /** @return the raw allocation date as supplied by TEFAS ({@code tarih}). */
    public String date() { return date; }

    /**
     * @return the dynamically collected, insertion-ordered map of allocation category code to
     *         non-zero percentage weight; populated by {@link #putUnknown(String, Object)}.
     */
    public Map<String, BigDecimal> allocations() { return allocations; }
}
