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
    private int tefasEodCutoverHour = 11;
    private int tefasMaxResponseMb = 32;
    private int tefasBulkPageSize = 100_000;
    private String tefasUserAgent =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";
}
