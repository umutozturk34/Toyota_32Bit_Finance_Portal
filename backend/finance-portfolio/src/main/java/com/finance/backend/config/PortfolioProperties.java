package com.finance.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.portfolio")
public class PortfolioProperties {

    private String defaultName = "Ana Portföy";
    private String defaultCurrency = "TRY";
    private BigDecimal initialBalance = new BigDecimal("1000000.0000");
    private BigDecimal minTransactionAmountTry = new BigDecimal("10");
}
