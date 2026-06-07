package com.finance.market.bond.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Externalized configuration ({@code app.bond.*}) for Turkish government bond
 * ingestion and analytics against the EVDS data provider.
 *
 * <p>Controls how bond series are fetched (batch sizes, the {@code bie_pydibs}
 * data group, the per-request day window) and the thresholds/constants used to
 * classify and value instruments: {@code rateThreshold}, {@code auctionThreshold}
 * and {@code cpiFixedThreshold} drive instrument-type detection, while
 * {@code faceValue} and {@code daysInYear} feed yield/price calculations.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.bond")
public class BondProperties {

    private int batchSize = 200;
    private int historyBatchSize = 8;
    private String datagroupCode = "bie_pydibs";
    private int maxDaysPerRequest = 1000;
    private BigDecimal rateThreshold = new BigDecimal("0.5");
    private BigDecimal auctionThreshold = new BigDecimal("14");
    private BigDecimal cpiFixedThreshold = new BigDecimal("5");
    private BigDecimal faceValue = new BigDecimal("100");
    private int daysInYear = 365;
}
