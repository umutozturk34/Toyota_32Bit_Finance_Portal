package com.finance.market.forex.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Forex ingest settings: EVDS datagroups, back-fill start/window, snapshot/latest lookbacks, EVDS
 * row cap (for pagination cut-off), batch sampling threshold, and per-currency flag emojis.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.forex")
public class ForexProperties {

    private String dovizDatagroup = "bie_dkdovytl";
    private String efektifDatagroup = "bie_dkefkytl";
    private LocalDate backfillStartDate = LocalDate.of(1995, 1, 1);
    private int batchMinSample = 5;
    private int snapshotLookbackDays = 5;
    private int backfillWindowDays = 4 * 365;
    private int evdsRowCap = 1000;
    private int latestLookbackDays = 5;
    // Max EVDS series codes per snapshot request. The cold-start snapshot spans every active currency (~100-160
    // codes); packing them all into one URL made EVDS drop the request (timeout/connection reset), which silently
    // aborted the whole refresh. Fetching in small fixed-size chunks keeps each request short and reliable.
    private int snapshotBatchChunkSize = 24;
    private Map<String, String> flagEmojis = new HashMap<>();
}
