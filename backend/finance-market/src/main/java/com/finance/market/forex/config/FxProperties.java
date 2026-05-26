package com.finance.market.forex.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.fx")
public class FxProperties {

    private int cacheTtlMinutes = 60;
    private long cacheMaxEntries = 1000;
    private int lookbackDays = 30;
}
