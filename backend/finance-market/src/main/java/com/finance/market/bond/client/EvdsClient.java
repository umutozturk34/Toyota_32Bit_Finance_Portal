package com.finance.market.bond.client;

import com.finance.common.config.AppProperties;
import com.finance.market.bond.config.BondProperties;
import com.finance.market.bond.dto.internal.EvdsBondDataResponse;
import com.finance.market.bond.dto.internal.EvdsBondSerieResponse;
import com.finance.common.exception.ExternalApiException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Log4j2
public class EvdsClient {

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final WebClient webClient;
    private final String datagroupCode;
    private final String serieListPath;
    private final String seriesPath;

    public EvdsClient(@Qualifier("bondWebClient") WebClient webClient,
                      AppProperties appProperties,
                      BondProperties bondProperties) {
        this.webClient = webClient;
        this.datagroupCode = bondProperties.getDatagroupCode();
        this.serieListPath = appProperties.getApi().getBond().getSerieListPath();
        this.seriesPath = appProperties.getApi().getBond().getSeriesPath();
    }

    @CircuitBreaker(name = "bond")
    @Retry(name = "bond")
    public List<EvdsBondSerieResponse> fetchBondSerieList() {
        log.debug("Fetching bond serie list from EVDS, datagroup: {}", datagroupCode);
        try {
            List<EvdsBondSerieResponse> series = webClient.get()
                    .uri(serieListPath + datagroupCode)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<EvdsBondSerieResponse>>() {})
                    .block();
            if (series == null || series.isEmpty()) {
                throw new ExternalApiException("EVDS", "Empty response from EVDS serie list");
            }
            log.debug("EVDS returned {} bond series", series.size());
            return series;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("EVDS", "Failed to fetch bond serie list from EVDS", e);
        }
    }

    @CircuitBreaker(name = "bond")
    @Retry(name = "bond")
    public EvdsBondDataResponse fetchBondData(List<String> serieCodes,
                                               String startDate,
                                               String endDate) {
        String seriesParam = String.join("-", serieCodes);
        log.debug("Fetching bond data: {} codes, period {} to {}", serieCodes.size(), startDate, endDate);
        try {
            EvdsBondDataResponse response = webClient.get()
                    .uri(seriesPath + seriesParam
                            + "&startDate=" + startDate
                            + "&endDate=" + endDate
                            + "&type=json")
                    .retrieve()
                    .bodyToMono(EvdsBondDataResponse.class)
                    .block();
            if (response == null) {
                throw new ExternalApiException("EVDS", "Empty response from EVDS bond data");
            }
            log.debug("EVDS bond data: {} items returned", response.totalCount());
            return response;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("EVDS", "Failed to fetch bond data from EVDS", e);
        }
    }
}
