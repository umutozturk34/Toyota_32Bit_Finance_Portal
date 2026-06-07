package com.finance.market.core.dto.internal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
/**
 * Maps the Yahoo Finance chart API response: meta (latest quote/day stats) plus parallel
 * timestamp/OHLC arrays under {@code indicators.quote}. Unknown JSON fields are ignored; bars may
 * contain nulls (gaps).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooChartResponse(Chart chart) {
    /**
     * Top-level {@code chart} envelope. Yahoo returns a list of results, one per requested symbol;
     * single-symbol calls yield a single-element list.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chart(List<Result> result) {
    }
    /**
     * One symbol's chart payload: the {@code meta} summary, the epoch-second {@code timestamp} axis,
     * and the {@code indicators} block holding the parallel OHLC/volume arrays.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            Meta meta,
            List<Long> timestamp,
            Indicators indicators
    ) {
        /**
         * Returns the first (and typically only) quote series, or {@code null} when no quote data is
         * present. Yahoo nests OHLC arrays inside {@code indicators.quote}; this guards against the
         * indicators block, the list, or the list's contents being absent.
         *
         * @return the first {@link Quote}, or {@code null} if unavailable
         */
        public Quote firstQuote() {
            return (indicators != null && indicators.quote() != null && !indicators.quote().isEmpty())
                    ? indicators.quote().getFirst()
                    : null;
        }
    }
    /**
     * Per-symbol summary block: the latest regular-session quote, prior close, intraday change
     * figures, day high/low, volume, naming, exchange, and currency. Any field may be absent
     * depending on the instrument and market state.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            @JsonProperty("regularMarketPrice") BigDecimal regularMarketPrice,
            @JsonProperty("chartPreviousClose") BigDecimal chartPreviousClose,
            @JsonProperty("previousClose") BigDecimal previousClose,
            @JsonProperty("regularMarketChange") BigDecimal regularMarketChange,
            @JsonProperty("regularMarketChangePercent") BigDecimal regularMarketChangePercent,
            @JsonProperty("regularMarketDayHigh") BigDecimal dayHigh,
            @JsonProperty("regularMarketDayLow") BigDecimal dayLow,
            @JsonProperty("regularMarketVolume") Long volume,
            String longName,
            String shortName,
            String exchangeName,
            String currency
    ) {}
    /**
     * Wrapper for the {@code indicators.quote} array of OHLC series carried alongside the
     * timestamp axis.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Indicators(List<Quote> quote) {}
    
    /**
     * Column-oriented OHLC and volume series. Each list is index-aligned with the enclosing
     * result's {@code timestamp} array; individual entries may be {@code null} where a bar has no
     * trade (gaps), so consumers must null-check per index.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Quote(
            List<BigDecimal> open,
            List<BigDecimal> high,
            List<BigDecimal> low,
            List<BigDecimal> close,
            List<Long> volume
    ) {
    }
}
