package com.finance.market.core.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Raw deserialization of the TCMB EVDS data API response: {@code totalCount} reported rows and the
 * {@code items} as untyped date-keyed series maps (column name to value), since EVDS series codes are
 * resolved dynamically per request. Unknown JSON properties are ignored so the contract tolerates
 * extra EVDS fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvdsDataResponse(
        @JsonProperty("totalCount") int totalCount,
        @JsonProperty("items") List<Map<String, Object>> items
) {}
