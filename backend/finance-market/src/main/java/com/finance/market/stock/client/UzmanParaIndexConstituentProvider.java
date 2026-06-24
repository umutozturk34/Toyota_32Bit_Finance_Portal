package com.finance.market.stock.client;

import com.finance.market.stock.config.StockProperties;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches the constituent stocks of a BIST index from a keyless index page (İş Yatırım's own index pages are
 * ASP.NET WebForms postbacks with no clean endpoint, so a different keyless source is used). The page is a
 * static HTML table whose rows link each member stock as {@code <a href="/borsa/hisse-senetleri/...">TICKER</a>};
 * a tolerant regex pulls the bare tickers. Any HTTP/parse failure returns an empty list (logged) so a single
 * bad index never breaks the refresh.
 */
@Log4j2
@Component
public class UzmanParaIndexConstituentProvider {

    // The ticker is the link text of a /borsa/hisse-senetleri/<slug>/ anchor; tolerant of attribute whitespace.
    private static final Pattern CONSTITUENT =
            Pattern.compile("/borsa/hisse-senetleri/[^\"]+\"[^>]*>\\s*([A-Z]{3,6})\\s*</a>");

    private final HttpClient http;
    private final StockProperties.Discovery discovery;

    public UzmanParaIndexConstituentProvider(StockProperties stockProperties) {
        this.discovery = stockProperties.getDiscovery();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(discovery.getConnectTimeoutSeconds()))
                .build();
    }

    /**
     * Member stock tickers (bare, e.g. {@code GARAN}) of the given index, or an empty list on any failure.
     *
     * @param indexCode bare BIST index code, e.g. {@code XBANK}
     */
    public List<String> fetchConstituents(String indexCode) {
        if (indexCode == null || indexCode.isBlank()) return List.of();
        String code = indexCode.trim().toUpperCase();
        try {
            URI uri = URI.create(discovery.getIndexConstituentUrlTemplate().replace("{code}", code));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(discovery.getRequestTimeoutSeconds()))
                    .header("User-Agent", discovery.getUserAgent())
                    .header("Accept", "text/html")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("Index constituents for {} returned status {}", code, response.statusCode());
                return List.of();
            }
            return parse(response.body());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Index constituents fetch failed for {}: {}", code, e.getMessage());
            return List.of();
        }
    }

    /** Extracts distinct constituent tickers (insertion order) from the static index-page HTML. */
    static List<String> parse(String html) {
        if (html == null || html.isBlank()) return List.of();
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = CONSTITUENT.matcher(html);
        while (m.find()) {
            seen.add(m.group(1));
        }
        return new ArrayList<>(seen);
    }
}
