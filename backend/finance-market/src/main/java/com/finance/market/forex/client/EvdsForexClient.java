package com.finance.market.forex.client;

import com.finance.common.config.AppProperties;
import com.finance.market.core.client.AbstractEvdsClient;
import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.core.dto.internal.EvdsSerieResponse;
import com.finance.market.forex.config.ForexProperties;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
@Log4j2
public class EvdsForexClient extends AbstractEvdsClient {

    private final String dovizDatagroup;
    private final String efektifDatagroup;

    public EvdsForexClient(@Qualifier("evdsWebClient") WebClient webClient,
                          AppProperties appProperties,
                          ForexProperties forexProperties) {
        super(webClient, appProperties);
        this.dovizDatagroup = forexProperties.getDovizDatagroup();
        this.efektifDatagroup = forexProperties.getEfektifDatagroup();
    }

    public List<EvdsSerieResponse> fetchDovizSerieList() {
        log.debug("Fetching forex döviz serie list, datagroup={}", dovizDatagroup);
        return fetchSerieListRaw(dovizDatagroup);
    }

    public List<EvdsSerieResponse> fetchEfektifSerieList() {
        log.debug("Fetching forex efektif serie list, datagroup={}", efektifDatagroup);
        return fetchSerieListRaw(efektifDatagroup);
    }

    public EvdsDataResponse fetchForexData(List<String> serieCodes, String startDate, String endDate) {
        log.debug("Fetching forex data: {} codes, period {} to {}", serieCodes.size(), startDate, endDate);
        return fetchDataRaw(serieCodes, startDate, endDate);
    }
}
