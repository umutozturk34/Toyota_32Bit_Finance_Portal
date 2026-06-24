package com.finance.market.stock.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Externalised configuration ({@code app.stock.*}) for equity (BIST) data.
 *
 * <p>Holds the default chart query parameters ({@code chartRange}, {@code chartInterval}),
 * the minimum sample required for batch operations, and the nested {@link Discovery}
 * settings governing automatic symbol discovery from İş Yatırım.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.stock")
public class StockProperties {

    private String chartRange = "max";
    private String chartInterval = "1d";
    private int batchMinSample = 10;
    /**
     * How many stocks to fetch from Yahoo concurrently during a full refresh. The candle endpoint is
     * per-symbol (no multi-symbol history call), so concurrency — not batching — is the lever; kept
     * conservative to stay under Yahoo's rate limits. Set to 1 to fall back to a sequential refresh.
     */
    private int updateParallelism = 6;
    /**
     * How long a company profile / index-membership enrichment stays fresh before the next stock refresh
     * re-scrapes it. Künye and index membership change slowly, so this stale-gate keeps enrichment riding
     * the regular refresh without re-fetching İş Yatırım for every stock on every run.
     */
    private Duration profileMaxAge = Duration.ofDays(7);
    private Discovery discovery = new Discovery();

    /**
     * Settings for scraping the BIST stock universe from the İş Yatırım listing page:
     * the source {@code baseUrl} and {@code userAgent}, connect/request timeouts, the
     * default sort order assigned to auto-tracked symbols, and the exchange {@code suffix}
     * ({@code .IS}) appended to discovered tickers.
     */
    @Getter
    @Setter
    public static class Discovery {
        private String baseUrl = "https://www.isyatirim.com.tr/tr-tr/analiz/hisse/Sayfalar/default.aspx";
        private String companyCardBaseUrl =
                "https://www.isyatirim.com.tr/tr-tr/analiz/hisse/Sayfalar/sirket-karti.aspx";
        // Keyless source for an index's constituent stocks (İş Yatırım's own index pages are ASP.NET
        // WebForms postbacks with no clean endpoint). {code} is the bare index code, e.g. XBANK.
        private String indexConstituentUrlTemplate =
                "https://uzmanpara.milliyet.com.tr/endeks-detay/{code}/hisseleri/";
        private String userAgent = "Mozilla/5.0 (compatible; FinancePortal/1.0)";
        private long connectTimeoutSeconds = 10;
        private long requestTimeoutSeconds = 20;
        private int autoTrackSortOrder = 9999;
        private String suffix = ".IS";
    }
}
