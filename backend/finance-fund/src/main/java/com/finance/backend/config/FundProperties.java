package com.finance.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.fund")
public class FundProperties {

    private int yearsToFetch = 5;
    private int minCandlesForIncremental = 30;
    private int windowSizes = 95;
}
