package com.finance.market.stock.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.stock")
public class StockProperties {

    private String chartRange = "max";
    private String chartInterval = "1d";
    private int batchMinSample = 10;
    private Discovery discovery = new Discovery();

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
