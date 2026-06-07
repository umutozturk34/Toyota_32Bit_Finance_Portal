package com.finance.market.core.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Describes a single EVDS (CBRT Electronic Data Delivery System) data series, mapped from the
 * serie-metadata endpoint. Carries the series identifier plus its Turkish and English names, the
 * available observation range, and the reporting frequency. Unknown JSON fields are ignored so the
 * record stays resilient to upstream additions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvdsSerieResponse(
        @JsonProperty("SERIE_CODE") String serieCode,
        @JsonProperty("SERIE_NAME") String serieName,
        @JsonProperty("SERIE_NAME_ENG") String serieNameEng,
        @JsonProperty("START_DATE") String startDate,
        @JsonProperty("END_DATE") String endDate,
        @JsonProperty("FREQUENCY_STR") String frequencyStr
) {

    /**
     * Convenience constructor for the common case where only the identifying code and Turkish name
     * are known; the English name, date range, and frequency are left {@code null}.
     *
     * @param serieCode the EVDS series code
     * @param serieName the series name (Turkish)
     */
    public EvdsSerieResponse(String serieCode, String serieName) {
        this(serieCode, serieName, null, null, null, null);
    }
}
