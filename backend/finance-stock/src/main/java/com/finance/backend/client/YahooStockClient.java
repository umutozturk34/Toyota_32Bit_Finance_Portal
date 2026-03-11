package com.finance.backend.client;
import com.finance.backend.dto.external.YahooStockQuoteDto;
import com.finance.backend.dto.internal.YahooChartResponse.Result;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.mapper.StockMapper;
import com.finance.backend.mapper.YahooCandleMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.time.ZoneId;
@Log4j2
@Component
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
            YahooStockQuoteDto dto = stockMapper.toQuoteDto(result, symbol);
            log.debug("Fetched snapshot for {}: {}", symbol, dto.currentPrice());
            return dto;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Snapshot fetch failed for {}", symbol, e);
            throw new ExternalApiException("Yahoo Finance", "Snapshot fetch failed for " + symbol, e);
        }
    }
}
