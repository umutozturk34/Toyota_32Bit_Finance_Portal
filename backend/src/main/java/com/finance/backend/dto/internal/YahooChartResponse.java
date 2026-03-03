package com.finance.backend.dto.internal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooChartResponse(Chart chart) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chart(List<Result> result) {
        public Result firstResult() {
            return (result != null && !result.isEmpty()) ? result.getFirst() : null;
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            Meta meta,
            List<Long> timestamp,
            Indicators indicators
    ) {
        public Quote firstQuote() {
            return (indicators != null && indicators.quote() != null && !indicators.quote().isEmpty())
                    ? indicators.quote().getFirst()
                    : null;
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            @JsonProperty("regularMarketPrice") BigDecimal regularMarketPrice,
            @JsonProperty("chartPreviousClose") BigDecimal chartPreviousClose,
            @JsonProperty("previousClose") BigDecimal previousClose,
            @JsonProperty("regularMarketDayHigh") BigDecimal dayHigh,
            @JsonProperty("regularMarketDayLow") BigDecimal dayLow,
            @JsonProperty("regularMarketVolume") Long volume,
            String longName,
            String shortName,
            String exchangeName,
            String currency
    ) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Indicators(List<Quote> quote) {}
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Quote(
            List<BigDecimal> open,
            List<BigDecimal> high,
            List<BigDecimal> low,
            List<BigDecimal> close,
            List<Long> volume
    ) {
        public boolean isValidAt(int index) {
            return safeGet(open, index) != null
                    && safeGet(high, index) != null
                    && safeGet(low, index) != null
                    && safeGet(close, index) != null;
        }
        private static <T> T safeGet(List<T> list, int index) {
            return (list != null && index < list.size()) ? list.get(index) : null;
        }
    }
}
