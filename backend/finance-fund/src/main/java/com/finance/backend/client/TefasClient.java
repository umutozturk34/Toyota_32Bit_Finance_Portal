package com.finance.backend.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.config.AppProperties;
import com.finance.backend.config.FundProperties;
import com.finance.backend.dto.external.TefasFundDto;
import com.finance.backend.dto.internal.TefasFundQueryRequest;
import com.finance.backend.dto.internal.TefasResponse;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.exception.ExternalApiRequestException;
import com.finance.backend.filter.TefasSessionManager;
import com.finance.backend.mapper.TefasClientMapper;
import com.finance.backend.model.FundType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Log4j2
@Component
public class TefasClient {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final String LANGUAGE = "TR";

    private final WebClient webClient;
    private final String tefasApiPath;
    private final ObjectMapper objectMapper;
    private final TefasClientMapper tefasClientMapper;
    private final TefasSessionManager sessionManager;
    private final int bulkPageSize;

    public TefasClient(@Qualifier("tefasWebClient") WebClient webClient,
                       AppProperties appProperties,
                       FundProperties fundProperties,
                       ObjectMapper objectMapper,
                       TefasClientMapper tefasClientMapper,
                       TefasSessionManager sessionManager) {
        this.webClient = webClient;
        this.tefasApiPath = appProperties.getTefasApiPath();
        this.objectMapper = objectMapper;
        this.tefasClientMapper = tefasClientMapper;
        this.sessionManager = sessionManager;
        this.bulkPageSize = fundProperties.getTefasBulkPageSize();
    }

    @CircuitBreaker(name = "tefas")
    @Retry(name = "tefas")
    public List<TefasFundDto> post(FundType fundType, String fundCode, LocalDate startDate, LocalDate endDate) {
        return executeRequest(fundType, fundCode, startDate, endDate, DEFAULT_PAGE_SIZE);
    }

    @CircuitBreaker(name = "tefas")
    @Retry(name = "tefas-bulk")
    public List<TefasFundDto> bulkFetch(FundType fundType, LocalDate startDate, LocalDate endDate) {
        return executeRequest(fundType, null, startDate, endDate, bulkPageSize);
    }

    private List<TefasFundDto> executeRequest(FundType fundType, String fundCode,
                                              LocalDate startDate, LocalDate endDate, int pageSize) {
        long reqStart = System.currentTimeMillis();
        log.debug("TEFAS request: type={}, code={}, from={}, to={}, pageSize={}",
                fundType, fundCode, startDate, endDate, pageSize);
        try {
            TefasFundQueryRequest request = new TefasFundQueryRequest(
                    fundType.name(),
                    fundCode,
                    null, null, null, null,
                    startDate.format(DATE_FORMAT),
                    endDate.format(DATE_FORMAT),
                    1, pageSize,
                    null, LANGUAGE, null);

            String body = doPost(request);

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


            long reqMs = System.currentTimeMillis() - reqStart;
            log.info("[TIMING] TEFAS call {}:{} ({} to {}) took {}ms", fundType, fundCode, startDate, endDate, reqMs);
            return parseResponse(body);
        } catch (ExternalApiException | ExternalApiRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("TEFAS",
                    "Request failed for " + fundType + " " + (fundCode != null ? fundCode : "all"), e);
        }
    }

    private String doPost(TefasFundQueryRequest request) {
        return webClient.post()
                .uri(tefasApiPath)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept", "*/*")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private List<TefasFundDto> parseResponse(String body) {
        String trimmed = body.trim();
        try {
            TefasResponse response = objectMapper.readValue(trimmed, TefasResponse.class);
            if (response.errorCode() != null
                    || (response.errorMessage() != null && !response.errorMessage().isBlank())) {
                throw new ExternalApiException("TEFAS",
                        "TEFAS error " + response.errorCode() + ": " + response.errorMessage());
            }
            if (response.resultList() == null || response.resultList().isEmpty()) {
                return List.of();
            }
            List<TefasFundDto> result = response.resultList().stream()
                    .map(tefasClientMapper::toDto)
                    .toList();
            log.debug("TEFAS returned {} records", result.size());
            return result;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("TEFAS parse failed. Response body (first 500 chars): {}", trimmed.substring(0, Math.min(trimmed.length(), 500)));
            throw new ExternalApiException("TEFAS", "Failed to parse TEFAS response", e);
        }
    }
}
