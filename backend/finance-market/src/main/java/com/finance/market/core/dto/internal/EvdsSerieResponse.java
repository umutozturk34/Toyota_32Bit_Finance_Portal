package com.finance.market.core.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EvdsSerieResponse(
        @JsonProperty("SERIE_CODE") String serieCode,
        @JsonProperty("SERIE_NAME") String serieName,
        @JsonProperty("SERIE_NAME_ENG") String serieNameEng,
        @JsonProperty("START_DATE") String startDate,
        @JsonProperty("END_DATE") String endDate,
        @JsonProperty("FREQUENCY_STR") String frequencyStr
) {

    public EvdsSerieResponse(String serieCode, String serieName) {
        this(serieCode, serieName, null, null, null, null);
    }
}
