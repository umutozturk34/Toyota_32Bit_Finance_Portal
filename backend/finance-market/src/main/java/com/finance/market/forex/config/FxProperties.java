package com.finance.market.forex.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Historical FX rate settings: per-pair series cache TTL/size and the max lookback (days) used when
 * falling back to the closest prior rate.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.fx")
public class FxProperties {

    private int cacheTtlMinutes = 60;
    private long cacheMaxEntries = 1000;
    private int lookbackDays = 30;
}
