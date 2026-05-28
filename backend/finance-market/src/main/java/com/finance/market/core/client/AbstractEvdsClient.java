package com.finance.market.core.client;

import com.finance.common.config.AppProperties;
import com.finance.common.exception.ExternalApiException;
import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.core.dto.internal.EvdsSerieResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Base EVDS (Turkish central bank) WebClient: serie-list and data fetches guarded by
 * circuit-breaker/retry, wrapping any failure in {@link ExternalApiException}. Subclasses bind
 * specific datagroups. {@link #DATE_FMT} is the EVDS date format ({@code dd-MM-yyyy}).
 */
@Log4j2
public abstract class AbstractEvdsClient {

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    protected final WebClient webClient;
    protected final String serieListPath;
    protected final String seriesPath;

    protected AbstractEvdsClient(WebClient webClient, AppProperties appProperties) {
        this.webClient = webClient;
        this.serieListPath = appProperties.getApi().getEvds().getSerieListPath();
        this.seriesPath = appProperties.getApi().getEvds().getSeriesPath();
    }

    @CircuitBreaker(name = "evds")
    @Retry(name = "evds")
    protected List<EvdsSerieResponse> fetchSerieListRaw(String datagroupCode) {
        log.debug("Fetching EVDS serie list, datagroup: {}", datagroupCode);
        try {
            List<EvdsSerieResponse> series = webClient.get()
                    .uri(serieListPath + datagroupCode)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<EvdsSerieResponse>>() {})
                    .block();
            if (series == null) {
                throw new ExternalApiException("EVDS", "Null response for serie list datagroup=" + datagroupCode);
            }
            log.debug("EVDS returned {} series for datagroup={}", series.size(), datagroupCode);
            return series;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("EVDS serie list fetch failed: datagroup={} cause={}: {}",
                    datagroupCode, e.getClass().getSimpleName(), e.getMessage(), e);
            throw new ExternalApiException("EVDS", "Failed to fetch serie list for datagroup=" + datagroupCode, e);
        }
    }

    /** Fetches data for the given serie codes over a date range; codes are dash-joined per the EVDS API. */
    @CircuitBreaker(name = "evds")
    @Retry(name = "evds")
    protected EvdsDataResponse fetchDataRaw(List<String> serieCodes, String startDate, String endDate) {
        String seriesParam = String.join("-", serieCodes);
        log.debug("Fetching EVDS data: {} codes, period {} to {}", serieCodes.size(), startDate, endDate);
        try {
            EvdsDataResponse response = webClient.get()
                    .uri(seriesPath + seriesParam
                            + "&startDate=" + startDate
                            + "&endDate=" + endDate
                            + "&type=json")
                    .retrieve()
                    .bodyToMono(EvdsDataResponse.class)
                    .block();
            if (response == null) {
                throw new ExternalApiException("EVDS", "Null response for data fetch");
            }
            log.debug("EVDS data response: {} items returned", response.totalCount());
            return response;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("EVDS data fetch failed: codes={} period={}..{} cause={}: {}",
                    serieCodes.size(), startDate, endDate,
                    e.getClass().getSimpleName(), e.getMessage(), e);
            throw new ExternalApiException("EVDS", "Failed to fetch data for codes count=" + serieCodes.size(), e);
        }
    }
}
