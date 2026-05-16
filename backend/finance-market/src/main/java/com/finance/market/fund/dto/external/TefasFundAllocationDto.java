package com.finance.market.fund.dto.external;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TefasFundAllocationDto {

    @JsonProperty("fonKodu") private String fundCode;
    @JsonProperty("fonUnvan") private String name;
    @JsonProperty("tarih") private String date;
    private final Map<String, BigDecimal> allocations = new LinkedHashMap<>();

    @JsonAnySetter
    public void putUnknown(String key, Object value) {
        if (value == null) return;
        if ("fonKodu".equals(key) || "fonUnvan".equals(key) || "tarih".equals(key) || "bilFiyat".equals(key)) return;
        if (value instanceof Number number) {
            BigDecimal decimal = new BigDecimal(number.toString());
            if (decimal.signum() != 0) allocations.put(key, decimal);
        }
    }

    public String fundCode() { return fundCode; }
    public String name() { return name; }
    public String date() { return date; }
    public Map<String, BigDecimal> allocations() { return allocations; }
}
