package com.finance.market.bond.client;

import com.finance.common.config.AppProperties;
import com.finance.market.bond.config.BondProperties;
import com.finance.market.core.client.AbstractEvdsClient;
import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.core.dto.internal.EvdsSerieResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
@Log4j2
public class EvdsBondClient extends AbstractEvdsClient {

    private final String datagroupCode;

    public EvdsBondClient(@Qualifier("evdsWebClient") WebClient webClient,
                         AppProperties appProperties,
                         BondProperties bondProperties) {
        super(webClient, appProperties);
        this.datagroupCode = bondProperties.getDatagroupCode();
    }

    public List<EvdsSerieResponse> fetchBondSerieList() {
        log.debug("Fetching bond serie list, datagroup={}", datagroupCode);
        return fetchSerieListRaw(datagroupCode);
    }

    public EvdsDataResponse fetchBondData(List<String> serieCodes, String startDate, String endDate) {
        log.debug("Fetching bond data: {} codes, period {} to {}", serieCodes.size(), startDate, endDate);
        return fetchDataRaw(serieCodes, startDate, endDate);
    }
}
