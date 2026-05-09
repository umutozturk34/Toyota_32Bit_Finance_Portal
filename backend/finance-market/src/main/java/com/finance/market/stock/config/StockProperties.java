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
}
