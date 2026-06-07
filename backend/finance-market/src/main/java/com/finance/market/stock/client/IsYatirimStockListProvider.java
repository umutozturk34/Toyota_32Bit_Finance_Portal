package com.finance.market.stock.client;

import com.finance.market.stock.config.StockProperties;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers BIST tickers by scraping the İş Yatırım stock-list page for {@code hisse=<TICKER>}
 * links; returns an empty list (logged) on any HTTP/parse failure so discovery never breaks refresh.
 */
@Log4j2
@Component
public class IsYatirimStockListProvider {

    private static final Pattern TICKER_PATTERN = Pattern.compile("hisse=([A-Z0-9]{2,8})");

    private final HttpClient http;
    private final StockProperties.Discovery discovery;

    public IsYatirimStockListProvider(StockProperties stockProperties) {
        this.discovery = stockProperties.getDiscovery();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(discovery.getConnectTimeoutSeconds()))
                .build();
    }

    public List<String> fetchTickers() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(discovery.getBaseUrl()))
                    .timeout(Duration.ofSeconds(discovery.getRequestTimeoutSeconds()))
                    .header("User-Agent", discovery.getUserAgent())
                    .header("Accept", "text/html")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("İş Yatırım stock list returned status {}", response.statusCode());
                return List.of();
            }
            return extractTickers(response.body());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("İş Yatırım stock list fetch failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** Extracts distinct tickers (insertion order) from the page HTML. */
    List<String> extractTickers(String html) {
        if (html == null || html.isBlank()) return List.of();
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = TICKER_PATTERN.matcher(html);
        while (m.find()) {
            seen.add(m.group(1));
        }
        return new ArrayList<>(seen);
    }
}
