package com.finance.backend.client;
import com.finance.backend.dto.external.YahooStockQuoteDto;
import com.finance.backend.dto.internal.YahooChartResponse.Result;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.mapper.StockMapper;
import com.finance.backend.mapper.YahooCandleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.time.ZoneId;
@Component
@Slf4j
public class YahooStockClient extends AbstractYahooClient {
    private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");
    private final StockMapper stockMapper;
    public YahooStockClient(@Qualifier("yahooRestTemplate") RestTemplate restTemplate,
                            @Value("${app.api.yahoo.base-url}") String baseUrl,
                            StockMapper stockMapper,
                            YahooCandleMapper candleMapper) {
        super(restTemplate, baseUrl, ISTANBUL_ZONE, true, candleMapper);
        this.stockMapper = stockMapper;
    }
    public YahooStockQuoteDto fetchSnapshot(String symbol) {
        try {
            Result result = fetchChart(symbol, "1d", "1d");
            return stockMapper.toQuoteDto(result, symbol);
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Yahoo Finance", "Snapshot fetch failed for " + symbol, e);
        }
    }
}
