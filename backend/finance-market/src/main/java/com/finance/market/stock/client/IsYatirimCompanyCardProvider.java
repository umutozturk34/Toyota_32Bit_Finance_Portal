package com.finance.market.stock.client;

import com.finance.market.stock.config.StockProperties;
import com.finance.market.stock.dto.external.CompanyCardDto;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches and parses an İş Yatırım company-card page to enrich a BIST stock with its künye (legal name,
 * sector, founding date, city) and the weighted list of indices it belongs to. The HTML is server-rendered
 * and static, so a targeted regex parse over the known {@code <th>label</th><td>value</td>} künye rows and
 * the "Dahil Olduğu Endekslerdeki Ağırlığı" table is sufficient. Any HTTP/parse failure returns null
 * (logged) so enrichment never breaks the stock refresh.
 */
@Log4j2
@Component
public class IsYatirimCompanyCardProvider {

    private static final DateTimeFormatter FOUNDED_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    // Header cell of the index table, e.g. >XU100 (%)< — captures the index code.
    private static final Pattern INDEX_CODE = Pattern.compile(">\\s*(X[A-Z0-9]+)\\s*\\(%\\)");
    // Numeric data cell, e.g. <td class="text-right">1,8</td> — captures a Turkish-formatted number.
    private static final Pattern NUMERIC_CELL = Pattern.compile("<td[^>]*>\\s*([0-9.,]+)\\s*</td>");

    private final HttpClient http;
    private final StockProperties.Discovery discovery;

    public IsYatirimCompanyCardProvider(StockProperties stockProperties) {
        this.discovery = stockProperties.getDiscovery();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(discovery.getConnectTimeoutSeconds()))
                .build();
    }

    /**
     * Fetches and parses the company card for a tracked stock symbol (e.g. {@code GARAN.IS} → ticker
     * {@code GARAN}); returns null on any failure so a single bad page never aborts the enrichment batch.
     */
    public CompanyCardDto fetch(String stockSymbol) {
        String ticker = bareTicker(stockSymbol);
        if (ticker.isBlank()) return null;
        try {
            URI uri = URI.create(discovery.getCompanyCardBaseUrl()
                    + "?hisse=" + URLEncoder.encode(ticker, StandardCharsets.UTF_8));
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
                log.warn("İş Yatırım company card for {} returned status {}", ticker, response.statusCode());
                return null;
            }
            return parse(response.body());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("İş Yatırım company card fetch failed for {}: {}", ticker, e.getMessage());
            return null;
        }
    }

    /** Strips the exchange suffix (e.g. {@code .IS}) to recover the bare İş Yatırım ticker. */
    static String bareTicker(String stockSymbol) {
        if (stockSymbol == null) return "";
        String s = stockSymbol.trim().toUpperCase();
        int dot = s.indexOf('.');
        return dot > 0 ? s.substring(0, dot) : s;
    }

    /** Parses the künye fields and index-weight table from the static company-card HTML. */
    static CompanyCardDto parse(String html) {
        if (html == null || html.isBlank()) return null;
        String legalName = blankToNull(field(html, "Ünvanı"));
        String sector = blankToNull(field(html, "Faal Alanı"));
        LocalDate founded = parseDate(field(html, "Kuruluş"));
        String city = cityFromAddress(field(html, "Adres"));
        return new CompanyCardDto(legalName, sector, founded, city, parseIndexWeights(html));
    }

    /** Extracts the value of a künye row {@code <th>label</th> <td ...>value</td>}, trimmed; null if absent. */
    private static String field(String html, String label) {
        Matcher m = Pattern.compile("<th>\\s*" + Pattern.quote(label) + "\\s*</th>\\s*<td[^>]*>(.*?)</td>",
                Pattern.DOTALL).matcher(html);
        return m.find() ? m.group(1).trim() : null;
    }

    /** Pairs each index header cell with its weight cell, by column position; empty when the table is absent. */
    private static List<CompanyCardDto.IndexWeight> parseIndexWeights(String html) {
        int anchor = html.indexOf("Dahil Olduğu Endekslerdeki");
        if (anchor < 0) return List.of();
        int tableStart = html.indexOf("<table", anchor);
        int tableEnd = tableStart < 0 ? -1 : html.indexOf("</table>", tableStart);
        if (tableStart < 0 || tableEnd < 0) return List.of();
        String table = html.substring(tableStart, tableEnd);

        List<String> codes = new ArrayList<>();
        Matcher codeMatcher = INDEX_CODE.matcher(table);
        while (codeMatcher.find()) {
            codes.add(codeMatcher.group(1));
        }
        int bodyStart = table.indexOf("<tbody");
        String body = bodyStart >= 0 ? table.substring(bodyStart) : table;
        List<BigDecimal> weights = new ArrayList<>();
        Matcher weightMatcher = NUMERIC_CELL.matcher(body);
        while (weightMatcher.find()) {
            BigDecimal weight = parseTurkishNumber(weightMatcher.group(1));
            if (weight != null) weights.add(weight);
        }

        List<CompanyCardDto.IndexWeight> memberships = new ArrayList<>();
        for (int i = 0; i < codes.size() && i < weights.size(); i++) {
            memberships.add(new CompanyCardDto.IndexWeight(codes.get(i), weights.get(i)));
        }
        return memberships;
    }

    /** The province segment of an address, taken after the final {@code /} (e.g. BEŞİKTAŞ/İSTANBUL → İSTANBUL). */
    private static String cityFromAddress(String address) {
        if (address == null || address.isBlank()) return null;
        int slash = address.lastIndexOf('/');
        String city = slash >= 0 ? address.substring(slash + 1) : address;
        city = city.trim();
        return city.isEmpty() ? null : city;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim(), FOUNDED_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }

    /** Parses a Turkish-formatted number ({@code .} thousands, {@code ,} decimal), e.g. {@code 1,8} → 1.8. */
    private static BigDecimal parseTurkishNumber(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new BigDecimal(raw.trim().replace(".", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
