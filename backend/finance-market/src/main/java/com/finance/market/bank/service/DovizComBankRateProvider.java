package com.finance.market.bank.service;

import com.finance.market.bank.dto.BankRateSnapshot;
import com.finance.market.bank.model.BankRateAssetKind;
import com.finance.market.bank.port.BankRateProvider;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scrapes per-bank buy/sell currency and gold rates from doviz.com. Each currency/gold instrument
 * has a fixed slug (and anchor bank) whose page lists bank rows; gold pages without a bank table
 * fall back to a single market quote. Network/parse failures degrade to an empty result per item.
 */
@Log4j2
@Component
public class DovizComBankRateProvider implements BankRateProvider {

    private static final String SOURCE = "DOVIZ_COM";
    private static final String DEFAULT_ANCHOR = "garanti-bbva";
    private static final String MARKET_BANK_CODE = "MARKET";
    private static final String MARKET_BANK_NAME = "Piyasa";

    private static final Map<String, String[]> CURRENCIES = new LinkedHashMap<>();
    static {
        CURRENCIES.put("USD", new String[]{"amerikan-dolari", "Amerikan Doları", DEFAULT_ANCHOR});
        CURRENCIES.put("EUR", new String[]{"euro", "Euro", DEFAULT_ANCHOR});
        CURRENCIES.put("GBP", new String[]{"sterlin", "İngiliz Sterlini", DEFAULT_ANCHOR});
        CURRENCIES.put("CHF", new String[]{"isvicre-frangi", "İsviçre Frangı", DEFAULT_ANCHOR});
        CURRENCIES.put("CAD", new String[]{"kanada-dolari", "Kanada Doları", DEFAULT_ANCHOR});
        CURRENCIES.put("AUD", new String[]{"avustralya-dolari", "Avustralya Doları", DEFAULT_ANCHOR});
        CURRENCIES.put("JPY", new String[]{"japon-yeni", "Japon Yeni", DEFAULT_ANCHOR});
        CURRENCIES.put("SAR", new String[]{"suudi-arabistan-riyali", "Suudi Arabistan Riyali", DEFAULT_ANCHOR});
        CURRENCIES.put("CNY", new String[]{"cin-yuani", "Çin Yuanı", DEFAULT_ANCHOR});
        CURRENCIES.put("DKK", new String[]{"danimarka-kronu", "Danimarka Kronu", DEFAULT_ANCHOR});
        CURRENCIES.put("SEK", new String[]{"isvec-kronu", "İsveç Kronu", DEFAULT_ANCHOR});
        CURRENCIES.put("NOK", new String[]{"norvec-kronu", "Norveç Kronu", DEFAULT_ANCHOR});
        CURRENCIES.put("RUB", new String[]{"rus-rublesi", "Rus Rublesi", "akbank"});
        CURRENCIES.put("QAR", new String[]{"katar-riyali", "Katar Riyali", "akbank"});
        CURRENCIES.put("KWD", new String[]{"kuveyt-dinari", "Kuveyt Dinarı", "akbank"});
        CURRENCIES.put("AED", new String[]{"birlesik-arap-emirlikleri-dirhemi", "BAE Dirhemi", "akbank"});
        CURRENCIES.put("ZAR", new String[]{"guney-afrika-randi", "Güney Afrika Randı", "akbank"});
        CURRENCIES.put("HKD", new String[]{"hong-kong-dolari", "Hong Kong Doları", "akbank"});
        CURRENCIES.put("PLN", new String[]{"polonya-zlotisi", "Polonya Zlotisi", "akbank"});
        CURRENCIES.put("RON", new String[]{"romen-leyi", "Romen Leyi", "akbank"});
        CURRENCIES.put("SGD", new String[]{"singapur-dolari", "Singapur Doları", "denizbank"});
        CURRENCIES.put("NZD", new String[]{"yeni-zelanda-dolari", "Yeni Zelanda Doları", "denizbank"});
        CURRENCIES.put("CZK", new String[]{"cek-korunasi", "Çek Korunası", "denizbank"});
        CURRENCIES.put("HUF", new String[]{"macar-forinti", "Macar Forinti", "denizbank"});
        CURRENCIES.put("INR", new String[]{"hindistan-rupisi", "Hindistan Rupisi", "denizbank"});
        CURRENCIES.put("THB", new String[]{"tayland-bahti", "Tayland Bahti", "denizbank"});
        CURRENCIES.put("MXN", new String[]{"meksika-pesosu", "Meksika Pesosu", "denizbank"});
        CURRENCIES.put("BRL", new String[]{"brezilya-reali", "Brezilya Reali", "denizbank"});
    }

    private static final Map<String, String[]> GOLDS = new LinkedHashMap<>();
    static {
        GOLDS.put("GRAM_ALTIN",        new String[]{"gram-altin", "Gram Altın"});
        GOLDS.put("CEYREK_ALTIN",      new String[]{"ceyrek-altin", "Çeyrek Altın"});
        GOLDS.put("YARIM_ALTIN",       new String[]{"yarim-altin", "Yarım Altın"});
        GOLDS.put("TAM_ALTIN",         new String[]{"tam-altin", "Tam Altın"});
        GOLDS.put("ATA_ALTIN",         new String[]{"ata-altin", "Ata Altın"});
        GOLDS.put("CUMHURIYET_ALTINI", new String[]{"cumhuriyet-altini", "Cumhuriyet Altını"});
        GOLDS.put("HAMIT_ALTIN",       new String[]{"hamit-altin", "Hamit Altın"});
        GOLDS.put("RESAT_ALTIN",       new String[]{"resat-altin", "Reşat Altın"});
        GOLDS.put("BESLI_ALTIN",       new String[]{"besli-altin", "Beşli Altın"});
        GOLDS.put("GRAM_HAS_ALTIN",    new String[]{"gram-has-altin", "Gram Has Altın"});
        GOLDS.put("AYAR_14",           new String[]{"14-ayar-altin", "14 Ayar Altın"});
        GOLDS.put("AYAR_18",           new String[]{"18-ayar-altin", "18 Ayar Altın"});
        GOLDS.put("AYAR_22_BILEZIK",   new String[]{"22-ayar-bilezik", "22 Ayar Bilezik"});
        GOLDS.put("GUMUS",             new String[]{"gumus", "Gümüş"});
    }

    private final HttpClient http;
    private final String currencyBaseUrl;
    private final String goldBaseUrl;
    private final String userAgent;
    private final Duration requestTimeout;

    public DovizComBankRateProvider(
            @Value("${app.api.doviz.currency-base-url:https://kur.doviz.com}") String currencyBaseUrl,
            @Value("${app.api.doviz.gold-base-url:https://altin.doviz.com}") String goldBaseUrl,
            @Value("${app.api.doviz.user-agent:Mozilla/5.0 (compatible; FinancePortal/1.0)}") String userAgent,
            @Value("${app.api.doviz.connect-timeout-seconds:5}") long connectTimeoutSeconds,
            @Value("${app.api.doviz.request-timeout-seconds:10}") long requestTimeoutSeconds
    ) {
        this.currencyBaseUrl = currencyBaseUrl;
        this.goldBaseUrl = goldBaseUrl;
        this.userAgent = userAgent;
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }

    @Override
    public String sourceId() {
        return SOURCE;
    }

    @Override
    public List<BankRateSnapshot> fetchAll() {
        List<BankRateSnapshot> result = new ArrayList<>();
        for (Map.Entry<String, String[]> e : CURRENCIES.entrySet()) {
            String code = e.getKey();
            String slug = e.getValue()[0];
            String displayName = e.getValue()[1];
            String anchor = e.getValue().length > 2 ? e.getValue()[2] : DEFAULT_ANCHOR;
            String url = currencyBaseUrl + "/" + anchor + "/" + slug;
            result.addAll(scrape(url, code, displayName, BankRateAssetKind.CURRENCY));
        }
        for (Map.Entry<String, String[]> e : GOLDS.entrySet()) {
            String code = e.getKey();
            String slug = e.getValue()[0];
            String displayName = e.getValue()[1];
            String url = goldBaseUrl + "/" + slug;
            result.addAll(scrape(url, code, displayName, BankRateAssetKind.GOLD));
        }
        return result;
    }

    private List<BankRateSnapshot> scrape(String url, String currencyCode, String currencyName,
                                           BankRateAssetKind kind) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .timeout(requestTimeout)
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("doviz.com {} {}: HTTP {}", kind, currencyCode, res.statusCode());
                return List.of();
            }
            return parse(res.body(), currencyCode, currencyName, kind);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("doviz.com {} {} scrape interrupted: {}", kind, currencyCode, ex.getMessage());
            return List.of();
        } catch (Exception ex) {
            log.warn("doviz.com {} {} scrape failed: {}", kind, currencyCode, ex.getMessage());
            return List.of();
        }
    }

    private List<BankRateSnapshot> parse(String html, String currencyCode, String currencyName,
                                          BankRateAssetKind kind) {
        Document doc = Jsoup.parse(html);
        Elements rows = doc.select("table[data-sortable] tbody tr");
        List<BankRateSnapshot> snaps = new ArrayList<>();
        if (rows.isEmpty() && kind == BankRateAssetKind.GOLD) {
            BankRateSnapshot market = extractMarketRow(doc, currencyCode, currencyName);
            if (market != null) snaps.add(market);
            return snaps;
        }
        for (Element row : rows) {
            Element link = row.selectFirst("td a[href]");
            Element img = row.selectFirst("td a img");
            Element bidCell = row.selectFirst("td[data-socket-attr=bid]");
            Element askCell = row.selectFirst("td[data-socket-attr=ask]");
            if (link == null || bidCell == null || askCell == null) continue;

            String href = link.attr("href");
            String bankSlug = extractBankSlug(href);
            if (bankSlug == null || bankSlug.isBlank()) continue;
            String bankName = link.ownText().trim();
            if (bankName.isEmpty() && img != null) bankName = img.attr("alt").trim();
            if (bankName.isEmpty()) bankName = bankSlug;
            String logoUrl = img != null ? img.attr("src") : null;
            BigDecimal buy = parseNumber(bidCell.text());
            BigDecimal sell = parseNumber(askCell.text());
            if (buy == null && sell == null) continue;

            snaps.add(new BankRateSnapshot(
                    SOURCE,
                    bankSlug.toUpperCase(java.util.Locale.ROOT).replace('-', '_'),
                    bankName,
                    logoUrl,
                    currencyCode,
                    currencyName,
                    kind,
                    buy,
                    sell));
        }
        log.debug("doviz.com {} {}: parsed {} bank rows", kind, currencyCode, snaps.size());
        return snaps;
    }

    private BankRateSnapshot extractMarketRow(Document doc, String currencyCode, String currencyName) {
        Element bid = doc.selectFirst("span[data-socket-attr=bid]");
        Element ask = doc.selectFirst("span[data-socket-attr=ask]");
        if (bid == null || ask == null) return null;
        BigDecimal buy = parseNumber(bid.text());
        BigDecimal sell = parseNumber(ask.text());
        if (buy == null && sell == null) return null;
        return new BankRateSnapshot(
                SOURCE,
                MARKET_BANK_CODE,
                MARKET_BANK_NAME,
                null,
                currencyCode,
                currencyName,
                BankRateAssetKind.GOLD,
                buy,
                sell);
    }

    private static String extractBankSlug(String href) {
        if (href == null) return null;
        String cleaned = href.split("[?#]", 2)[0];
        String[] parts = cleaned.split("/");
        if (parts.length < 2) return null;
        return parts[parts.length - 2];
    }

    /** Parses a Turkish-locale number (thousands '.', decimal ','), returning null on failure. */
    private static BigDecimal parseNumber(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replace(".", "").replace(',', '.').replaceAll("[^0-9.\\-]", "").trim();
        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals(".")) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
