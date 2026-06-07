package com.finance.market.fund.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised configuration ({@code app.fund.*}) for fund (TEFAS) data ingestion.
 *
 * <p>Controls history depth and incremental refresh thresholds, TEFAS request shaping
 * (page sizes, response-size limit, language, browser User-Agent) and the various
 * look-back windows used for holiday, gap and allocation detection. The EOD cutover
 * hour distinguishes same-day provisional NAVs from finalised ones.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.fund")
public class FundProperties {

    private int yearsToFetch = 5;
    private int minCandlesForIncremental = 30;
    private int windowSizes = 28;
    private int tefasEodCutoverHour = 11;
    private int tefasMaxResponseMb = 32;
    private int tefasBulkPageSize = 100_000;
    private int tefasDefaultPageSize = 100;
    private String tefasLanguage = "TR";
    private String tefasUserAgent =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private int autoTrackSortOrder = 9999;
    private int holidayLookbackDays = 5;
    private int gapDetectionLookbackDays = 30;
    private int allocationWalkbackDays = 7;
}
