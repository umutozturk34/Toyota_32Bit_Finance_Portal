package com.finance.market.forex.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ForexDataService {

    private final ForexUpdateService forexUpdateService;

    public void syncAllYahoo() {
        forexUpdateService.refreshAll();
    }
}
