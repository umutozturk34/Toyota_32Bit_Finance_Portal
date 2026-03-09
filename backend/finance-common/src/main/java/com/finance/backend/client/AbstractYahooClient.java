    package com.finance.backend.client;

    import com.finance.backend.dto.external.YahooCandleDto;
    import com.finance.backend.dto.internal.YahooChartResponse;
    import com.finance.backend.dto.internal.YahooChartResponse.Result;
    import com.finance.backend.exception.ExternalApiException;
    import com.finance.backend.mapper.YahooCandleMapper;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.web.client.RestTemplate;

    import java.time.ZoneId;
    import java.util.List;

    @Slf4j
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
                return candleMapper.toCandleDtos(result, zoneId, truncateToDays);
            } catch (ExternalApiException e) {
                throw e;
            } catch (Exception e) {
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
                throw new ExternalApiException("Yahoo Finance", "Request failed: " + url, e);
            }
        }
    }
