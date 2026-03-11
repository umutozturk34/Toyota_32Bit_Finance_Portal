    package com.finance.backend.client;

    import com.finance.backend.dto.external.YahooCandleDto;
    import com.finance.backend.dto.internal.YahooChartResponse;
    import com.finance.backend.dto.internal.YahooChartResponse.Result;
    import com.finance.backend.exception.ExternalApiException;
    import com.finance.backend.mapper.YahooCandleMapper;
    import lombok.extern.log4j.Log4j2;
    import org.springframework.web.client.RestTemplate;

    import java.time.ZoneId;
    import java.util.List;

    @Log4j2
    public abstract class AbstractYahooClient {
        protected final RestTemplate restTemplate;
        protected final String baseUrl;
        private final ZoneId zoneId;
        private final boolean truncateToDays;
        private final YahooCandleMapper candleMapper;

        protected AbstractYahooClient(RestTemplate restTemplate, String baseUrl,
                                    ZoneId zoneId, boolean truncateToDays,
                                    YahooCandleMapper candleMapper) {
            this.restTemplate = restTemplate;
            this.baseUrl = baseUrl;
            this.zoneId = zoneId;
            this.truncateToDays = truncateToDays;
            this.candleMapper = candleMapper;
        }

        public List<YahooCandleDto> fetchCandles(String symbol, String range, String interval) {
            try {
                Result result = fetchChart(symbol, range, interval);
                List<YahooCandleDto> candles = candleMapper.toCandleDtos(result, zoneId, truncateToDays);
                log.debug("Fetched {} candles for {} (range={}, interval={})", candles.size(), symbol, range, interval);
                return candles;
            } catch (ExternalApiException e) {
                throw e;
            } catch (Exception e) {
                log.error("Candle fetch failed for {}", symbol, e);
                throw new ExternalApiException("Yahoo Finance", "Candle fetch failed for " + symbol, e);
            }
        }

        protected Result fetchChart(String symbol, String range, String interval) {
            String url = baseUrl + symbol + "?range=" + range + "&interval=" + interval;
            try {
                YahooChartResponse response = restTemplate.getForObject(url, YahooChartResponse.class);
                if (response == null || response.chart() == null) {
                    throw new ExternalApiException("Yahoo Finance", "Empty response for " + symbol);
                }
                Result result = response.chart().firstResult();
                if (result == null) {
                    throw new ExternalApiException("Yahoo Finance", "No chart data for " + symbol);
                }
                return result;
            } catch (ExternalApiException e) {
                throw e;
            } catch (Exception e) {
                log.error("Chart request failed: {}", url, e);
                throw new ExternalApiException("Yahoo Finance", "Request failed: " + url, e);
            }
        }
    }
