package com.finance.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Api api = new Api();
    private Tcmb tcmb = new Tcmb();
    private String tefasApiPath;
    private String tefasBaseUrl;
    private String timezone = "Europe/Istanbul";
    private int scale = 4;
    private Http http = new Http();
    private Async async = new Async();
    private Crypto crypto = new Crypto();
    private Stock stock = new Stock();
    private Forex forex = new Forex();
    private Fund fund = new Fund();
    private RateLimit rateLimit = new RateLimit();
    private Bond bond = new Bond();
    private News news = new News();

    @Getter
    @Setter
    public static class Api {
        private CoinGeckoProvider coingecko = new CoinGeckoProvider();
        private Provider yahoo = new Provider();
        private Provider binance = new Provider();
        private BondProvider bond = new BondProvider();
    }

    @Getter
    @Setter
    public static class Provider {
        private String baseUrl;
        private String userAgent;
    }

    @Getter
    @Setter
    public static class CoinGeckoProvider extends Provider {
        private String apiKeyHeader = "x-cg-demo-api-key";
    }

    @Getter
    @Setter
    public static class BondProvider extends Provider {
        private String apiKeyHeader = "key";
    }

    @Getter
    @Setter
    public static class Tcmb {
        private String baseUrl;
        private String xmlPath;
    }

    @Getter
    @Setter
    public static class Http {
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 30000;
        private String defaultUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    }

    @Getter
    @Setter
    public static class Async {
        private int corePoolSize = 3;
        private int maxPoolSize = 5;
        private int queueCapacity = 25;
    }

    @Getter
    @Setter
    public static class Crypto {
        private int historyDays = 365;
        private int minCandlesForHealthy = 350;
    }

    @Getter
    @Setter
    public static class Bond {
        private int batchSize = 10;
        private String datagroupCode = "bie_pydibs";
        private int maxDaysPerRequest = 1000;
        private BigDecimal rateThreshold = new BigDecimal("0.5");
        private BigDecimal auctionThreshold = new BigDecimal("14");
        private BigDecimal cpiFixedThreshold = new BigDecimal("5");
        private BigDecimal faceValue = new BigDecimal("100");
        private int daysInYear = 365;
    }

    @Getter
    @Setter
    public static class Stock {
        private int minCandlesForIncremental = 1200;
        private int historyYears = 5;
    }

    @Getter
    @Setter
    public static class Forex {
        private int yearsToKeep = 5;
        private int minCandlesForIncremental = 1200;
        private BigDecimal spreadRate = new BigDecimal("0.01");
    }

    @Getter
    @Setter
    public static class Fund {
        private int yearsToFetch = 5;
        private int minCandlesForIncremental = 30;
        private int windowSizes = 95;
    }

    @Getter
    @Setter
    public static class News {
        private int maxArticlesPerSource = 50;
        private int cacheTtlHours = 24;
        private List<NewsSource> sources = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class NewsSource {
        private String name;
        private String url;
        private String type = "RSS";
        private String defaultCategory;
    }

    @Getter
    @Setter
    public static class RateLimit {
        private int adminTriggerLimit = 3;
        private int adminReadLimit = 20;
        private int apiLimit = 60;
    }
}
