package com.finance.backend.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.TefasFundDto;
import com.finance.backend.dto.internal.TefasResponse;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.exception.ExternalApiRequestException;
import com.finance.backend.filter.TefasSessionManager;
import com.finance.backend.mapper.TefasClientMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Log4j2
@Component
public class TefasClient {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final WebClient webClient;
    private final String tefasApiPath;
    private final ObjectMapper objectMapper;
    private final TefasClientMapper tefasClientMapper;
    private final TefasSessionManager sessionManager;

    public TefasClient(@Qualifier("tefasWebClient") WebClient webClient,
                       AppProperties appProperties,
                       ObjectMapper objectMapper,
                       TefasClientMapper tefasClientMapper,
                       TefasSessionManager sessionManager) {
        this.webClient = webClient;
        this.tefasApiPath = appProperties.getTefasApiPath();
        this.objectMapper = objectMapper;
        this.tefasClientMapper = tefasClientMapper;
        this.sessionManager = sessionManager;
    }

    @CircuitBreaker(name = "tefas")
    @Retry(name = "tefas")
    public List<TefasFundDto> post(String fundType, String fundCode, LocalDate startDate, LocalDate endDate) {
        log.debug("TEFAS request: type={}, code={}, from={}, to={}", fundType, fundCode, startDate, endDate);
        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("fontip", fundType);
            formData.add("bastarih", startDate.format(DATE_FORMAT));
            formData.add("bittarih", endDate.format(DATE_FORMAT));
            if (fundCode != null && !fundCode.isBlank()) {
                formData.add("fonkod", fundCode);
            }

            String body = doPost(formData);

            if (body != null && body.trim().startsWith("<")) {
                log.warn("TEFAS WAF block: {} {} ({} to {})", fundType, fundCode, startDate, endDate);
                throw new ExternalApiRequestException("TEFAS",
                        "WAF blocked request for " + fundType + " " + (fundCode != null ? fundCode : "all")
                        + " (range " + startDate + " to " + endDate + ")");
            }

            if (body == null || body.isBlank()) {
                log.warn("TEFAS empty body for {} {}, refreshing session", fundType, fundCode);
                sessionManager.invalidate();
                throw new ExternalApiException("TEFAS",
                            "Empty response after session refresh for " + fundType
                            + " " + (fundCode != null ? fundCode : "all"));
                }


            return parseResponse(body);
        } catch (ExternalApiException | ExternalApiRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("TEFAS",
                    "Request failed for " + fundType + " " + (fundCode != null ? fundCode : "all"), e);
        }
    }

    private String doPost(MultiValueMap<String, String> formData) {
        return webClient.post()
                .uri(tefasApiPath)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Accept", "*/*")
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private List<TefasFundDto> parseResponse(String body) {
        String trimmed = body.trim();
        try {
            TefasResponse response = objectMapper.readValue(trimmed, TefasResponse.class);
            if (response.recordsTotal() == 0) {
                return List.of();
            }
            List<TefasFundDto> result = response.data().stream()
                    .map(tefasClientMapper::toDto)
                    .toList();
            log.debug("TEFAS returned {} records", result.size());
            return result;
        } catch (Exception e) {
            log.error("TEFAS parse failed. Response body (first 500 chars): {}", trimmed.substring(0, Math.min(trimmed.length(), 500)));
            throw new ExternalApiException("TEFAS", "Failed to parse TEFAS response", e);
        }
    }
}
