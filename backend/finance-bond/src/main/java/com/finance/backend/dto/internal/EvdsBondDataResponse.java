package com.finance.backend.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EvdsBondDataResponse(
        @JsonProperty("totalCount") int totalCount,
        @JsonProperty("items") List<Map<String, Object>> items
) {}
