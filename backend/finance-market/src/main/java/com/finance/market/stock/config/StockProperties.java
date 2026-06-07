package com.finance.market.stock.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
        private String userAgent = "Mozilla/5.0 (compatible; FinancePortal/1.0)";
        private long connectTimeoutSeconds = 10;
        private long requestTimeoutSeconds = 20;
        private int autoTrackSortOrder = 9999;
        private String suffix = ".IS";
    }
}
