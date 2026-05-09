package com.finance.market.forex.config;
import com.finance.market.core.service.MarketSnapshotProcessor;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.forex")
public class ForexProperties {

    private BigDecimal spreadRate = new BigDecimal("0.01");
    private String chartRange = "max";
    private String chartInterval = "1d";
    private int batchMinSample = 5;
    private String baseCurrency = "USDTRY";
}
