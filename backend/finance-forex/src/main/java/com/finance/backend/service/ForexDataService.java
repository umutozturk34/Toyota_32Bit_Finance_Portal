package com.finance.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ForexDataService {

    private final ForexSnapshotService yahooForexSnapshotService;
    private final ForexCandleService yahooForexCandleService;

    public void syncAllYahooSnapshots() {
        yahooForexSnapshotService.refreshAll();
    }

    public void syncAllYahooCandles() {
        yahooForexCandleService.refreshAll();
    }
}
