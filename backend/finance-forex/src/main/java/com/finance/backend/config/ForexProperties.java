package com.finance.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.forex")
public class ForexProperties {

    private int yearsToKeep = 5;
    private BigDecimal spreadRate = new BigDecimal("0.01");
    private String chartRange = "5y";
    private String chartInterval = "1d";
    private int batchMinSample = 5;
    private String baseCurrency = "USDTRY";
}
