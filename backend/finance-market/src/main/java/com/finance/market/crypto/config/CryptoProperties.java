package com.finance.market.crypto.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Crypto ingest settings: history window, healthy-candle threshold (below which history is fully
 * reloaded), Binance page size/interval, batch sampling, and the CoinGecko vs-currencies (usd/try).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.crypto")
public class CryptoProperties {

    private int historyDays = 365;
    private int minCandlesForHealthy = 350;
    private int batchMinSample = 5;
    private int binancePageSize = 1000;
    private String binanceInterval = "1d";
    private String vsUsd = "usd";
    private String vsTry = "try";
}
