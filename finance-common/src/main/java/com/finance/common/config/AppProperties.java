package com.finance.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String tefasApiPath;
    private String tefasBaseUrl;
    private String tefasSessionPath = "/tr";
    private String timezone = "Europe/Istanbul";
    private int scale = 4;

    private Api api = new Api();
    private Tcmb tcmb = new Tcmb();

    private Http http = new Http();
    private Async async = new Async();
    private Cache cache = new Cache();

    private Scheduler scheduler = new Scheduler();

    private Pagination pagination = new Pagination();

    private Task task = new Task();
    private TrackedAsset trackedAsset = new TrackedAsset();

    private Security security = new Security();
    private RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class Api {
        private CoinGeckoProvider coingecko = new CoinGeckoProvider();
        private YahooProvider yahoo = new YahooProvider();
        private BinanceProvider binance = new BinanceProvider();
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
        private String marketsPath = "/coins/markets";
    }

    @Getter
    @Setter
    public static class BondProvider extends Provider {
        private String apiKeyHeader = "key";
        private String serieListPath = "/serieList/type=json&code=";
        private String seriesPath = "/series=";
    }

    @Getter
    @Setter
    public static class YahooProvider extends Provider {
        private String chartPath = "";
    }

    @Getter
    @Setter
    public static class BinanceProvider extends Provider {
        private String klinesPath = "/api/v3/klines";
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
        private int bondMaxInMemorySizeMb = 10;
        private int newsMaxInMemorySizeMb = 5;
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
    public static class Scheduler {
        private MarketSchedule crypto = new MarketSchedule();
        private MarketSchedule stock = new MarketSchedule();
        private MarketSchedule forex = new MarketSchedule();
        private MarketSchedule commodity = new MarketSchedule();
        private MarketSchedule news = new MarketSchedule();
        private SingleSchedule fund = new SingleSchedule();
        private SingleSchedule bond = new SingleSchedule();
        private PortfolioSchedule portfolio = new PortfolioSchedule();
    }

    @Getter
    @Setter
    public static class MarketSchedule {
        private String morningCron;
        private String afternoonCron;
        private String eveningCron;
    }

    @Getter
    @Setter
    public static class SingleSchedule {
        private String dailyCron;
    }

    @Getter
    @Setter
    public static class PortfolioSchedule {
        private String snapshotCron;
    }

    @Getter
    @Setter
    public static class Pagination {
        private Market market = new Market();
        private BondPage bond = new BondPage();
        private NewsPage news = new NewsPage();
        private PortfolioPage portfolio = new PortfolioPage();
    }

    @Getter
    @Setter
    public static class Market {
        private int defaultSize = 20;
        private int maxSize = 100;
        private int defaultOverviewLimit = 5;
        private int maxOverviewLimit = 20;
    }

    @Getter
    @Setter
    public static class BondPage {
        private int defaultSize = 8;
        private int maxSize = 100;
    }

    @Getter
    @Setter
    public static class NewsPage {
        private int defaultSize = 10;
        private int maxSize = 100;
    }

    @Getter
    @Setter
    public static class PortfolioPage {
        private int transactionsDefaultSize = 10;
        private int positionsDefaultSize = 10;
        private int maxSize = 100;
    }

    @Getter
    @Setter
    public static class Task {
        private int maxHistory = 50;
    }

    @Getter
    @Setter
    public static class TrackedAsset {
        private long codeCacheTtlSeconds = 45;
    }

    @Getter
    @Setter
    public static class Security {
        private Cors cors = new Cors();
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
                "http://localhost",
                "http://localhost:*",
                "http://127.0.0.1",
                "http://127.0.0.1:*"
        ));
        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        private List<String> allowedHeaders = new ArrayList<>(List.of("Authorization", "Content-Type", "Cache-Control", "X-Requested-With"));
        private List<String> exposedHeaders = new ArrayList<>(List.of("Authorization"));
        private boolean allowCredentials = true;
        private long maxAgeSeconds = 3600;
    }

    @Getter
    @Setter
    public static class Cache {
        private int redisDefaultTtlHours = 24;
    }

    @Getter
    @Setter
    public static class RateLimit {
        private int adminTriggerLimit = 3;
        private int adminReadLimit = 20;
        private int apiLimit = 60;
        private int credentialActionLimit = 3;
    }
}
