package com.finance.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.crypto")
public class CryptoProperties {

    private int historyDays = 365;
    private int minCandlesForHealthy = 350;
    private int batchMinSample = 5;
    private String vsUsd = "usd";
    private String vsTry = "try";
}
