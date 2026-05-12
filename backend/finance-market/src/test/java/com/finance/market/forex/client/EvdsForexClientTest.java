package com.finance.market.forex.client;

import com.finance.common.config.AppProperties;
import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.core.dto.internal.EvdsSerieResponse;
import com.finance.market.forex.config.ForexProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvdsForexClientTest {

    private WebClient webClient;
    private EvdsForexClient client;

    @BeforeEach
    void setUp() {
        webClient = mock(WebClient.class, RETURNS_DEEP_STUBS);
        AppProperties appProperties = new AppProperties();
        appProperties.getApi().getEvds().setSerieListPath("/serieList/type=json&code=");
        appProperties.getApi().getEvds().setSeriesPath("/series=");
        ForexProperties forexProperties = new ForexProperties();
        forexProperties.setDovizDatagroup("bie_dkdovytl");
        forexProperties.setEfektifDatagroup("bie_dkefkytl");
        client = new EvdsForexClient(webClient, appProperties, forexProperties);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_fetchDovizSerieList_when_calledWithDovizDatagroup() {
        List<EvdsSerieResponse> stub = List.of(new EvdsSerieResponse("TP.DK.USD.A.YTL", "USD"));
        when(webClient.get().uri(contains("bie_dkdovytl"))
                .retrieve()
                .bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn((Mono) Mono.just(stub));

        List<EvdsSerieResponse> result = client.fetchDovizSerieList();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().serieCode()).isEqualTo("TP.DK.USD.A.YTL");
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_fetchEfektifSerieList_when_calledWithEfektifDatagroup() {
        List<EvdsSerieResponse> stub = List.of(new EvdsSerieResponse("TP.DK.USD.A.EF.YTL", "USD"));
        when(webClient.get().uri(contains("bie_dkefkytl"))
                .retrieve()
                .bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn((Mono) Mono.just(stub));

        List<EvdsSerieResponse> result = client.fetchEfektifSerieList();

        assertThat(result.getFirst().serieCode()).isEqualTo("TP.DK.USD.A.EF.YTL");
    }

    @Test
    void should_fetchForexData_when_calledWithSeriesCodes() {
        EvdsDataResponse stub = new EvdsDataResponse(1, List.of());
        when(webClient.get().uri(contains("TP.DK.USD.A.YTL"))
                .retrieve()
                .bodyToMono(EvdsDataResponse.class))
                .thenReturn(Mono.just(stub));

        EvdsDataResponse result = client.fetchForexData(
                List.of("TP.DK.USD.A.YTL", "TP.DK.USD.S.YTL"),
                "01-05-2026", "11-05-2026");

        assertThat(result.totalCount()).isEqualTo(1);
        verify(webClient.get()).uri(contains("TP.DK.USD.A.YTL"));
    }
}
