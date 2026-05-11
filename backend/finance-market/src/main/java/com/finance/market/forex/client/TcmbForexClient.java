package com.finance.market.forex.client;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.finance.common.config.AppProperties;
import com.finance.market.forex.dto.external.TcmbRateDto;
import com.finance.market.forex.dto.internal.TcmbXmlResponse;
import com.finance.common.exception.ExternalApiException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
@Log4j2
public class TcmbForexClient {

    private final WebClient webClient;
    private final String xmlPath;
    private final XmlMapper xmlMapper;

    public TcmbForexClient(@Qualifier("tcmbWebClient") WebClient webClient,
                           AppProperties appProperties) {
        this.webClient = webClient;
        this.xmlPath = appProperties.getTcmb().getXmlPath();
        this.xmlMapper = XmlMapper.builder()
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    @CircuitBreaker(name = "tcmb")
    @Retry(name = "tcmb")
    public List<TcmbRateDto> fetchDailyRates() {
        log.debug("Fetching daily rates from TCMB");
        try {
            byte[] body = webClient.get()
                    .uri(xmlPath)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
            if (body == null || body.length == 0) {
                throw new ExternalApiException("TCMB", "Empty response from TCMB XML service");
            }
            TcmbXmlResponse response = xmlMapper.readValue(body, TcmbXmlResponse.class);
            List<TcmbRateDto> rates = response.currencies().stream()
                    .filter(dto -> !"XDR".equals(dto.currencyCode()))
                    .toList();
            log.debug("TCMB returned {} currency rates", rates.size());
            return rates;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("TCMB", "Failed to fetch daily rates from TCMB", e);
        }
    }
}
