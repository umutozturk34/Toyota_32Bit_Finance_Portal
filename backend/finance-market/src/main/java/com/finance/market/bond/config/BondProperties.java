package com.finance.market.bond.config;
import com.finance.market.core.service.MarketSnapshotProcessor;

import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.bond")
public class BondProperties {

    private int batchSize = 10;
    private String datagroupCode = "bie_pydibs";
    private int maxDaysPerRequest = 1000;
    private BigDecimal rateThreshold = new BigDecimal("0.5");
    private BigDecimal auctionThreshold = new BigDecimal("14");
    private BigDecimal cpiFixedThreshold = new BigDecimal("5");
    private BigDecimal faceValue = new BigDecimal("100");
    private int daysInYear = 365;
}
