package com.finance.backend.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EvdsBondSerieResponse(
        @JsonProperty("SERIE_CODE") String serieCode,
        @JsonProperty("SERIE_NAME") String serieName
) {}
