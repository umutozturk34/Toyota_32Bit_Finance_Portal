package com.finance.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.commission")
public class CommissionProperties {

    private BigDecimal stockRate = new BigDecimal("0.002");
    private BigDecimal cryptoRate = new BigDecimal("0.0015");
    private BigDecimal fundRate = new BigDecimal("0.001");
    private BigDecimal commodityRate = new BigDecimal("0.015");
}
