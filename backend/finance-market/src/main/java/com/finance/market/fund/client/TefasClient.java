package com.finance.market.fund.client;


import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import com.finance.common.config.AppProperties;
import com.finance.market.fund.config.FundProperties;
import com.finance.market.fund.dto.external.TefasFundAllocationDto;
import com.finance.market.fund.dto.external.TefasFundDto;
import com.finance.market.fund.dto.external.TefasFundInfoDto;
import com.finance.market.fund.dto.external.TefasFundProfileDto;
import com.finance.market.fund.dto.external.TefasFundReturnsDto;
import com.finance.market.fund.dto.internal.TefasFundQueryRequest;
import com.finance.market.fund.dto.internal.TefasGenericResponse;
import com.finance.market.fund.dto.internal.TefasResponse;
import com.finance.common.exception.ExternalApiException;
import com.finance.shared.exception.ExternalApiRequestException;
import com.finance.market.core.filter.TefasSessionManager;
import com.finance.market.fund.mapper.TefasClientMapper;
import com.finance.market.fund.model.FundType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TEFAS client for fund prices, returns, allocations, info and profile. All calls are guarded by
 * circuit-breaker/retry and carry the session cookie; WAF blocks (HTML body) and empty bodies are
 * detected and surfaced as external errors (invalidating the session), and TEFAS error codes throw.
 */
@Log4j2
@Component
public class TefasClient {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WebClient webClient;
    private final String tefasApiPath;
    private final String tefasReturnsPath;
    private final String tefasAllocationPath;
    private final String tefasInfoPath;
    private final String tefasProfilePath;
    private final ObjectMapper objectMapper;
    private final TefasClientMapper tefasClientMapper;
    private final TefasSessionManager sessionManager;
    private final int bulkPageSize;
    private final int defaultPageSize;
    private final String language;

    public TefasClient(@Qualifier("tefasWebClient") WebClient webClient,
                       AppProperties appProperties,
                       FundProperties fundProperties,
                       ObjectMapper objectMapper,
                       TefasClientMapper tefasClientMapper,
                       TefasSessionManager sessionManager) {
        this.webClient = webClient;
        this.tefasApiPath = appProperties.getTefasApiPath();
        this.tefasReturnsPath = appProperties.getTefasReturnsPath();
        this.tefasAllocationPath = appProperties.getTefasAllocationPath();
        this.tefasInfoPath = appProperties.getTefasInfoPath();
        this.tefasProfilePath = appProperties.getTefasProfilePath();
        this.objectMapper = objectMapper;
        this.tefasClientMapper = tefasClientMapper;
        this.sessionManager = sessionManager;
        this.bulkPageSize = fundProperties.getTefasBulkPageSize();
        this.defaultPageSize = fundProperties.getTefasDefaultPageSize();
        this.language = fundProperties.getTefasLanguage();
    }

    @CircuitBreaker(name = "tefas")
    @Retry(name = "tefas")
    public List<TefasFundDto> post(FundType fundType, String fundCode, LocalDate startDate, LocalDate endDate) {
        return executeRequest(fundType, fundCode, startDate, endDate, defaultPageSize);
    }

    /** Fetches all funds of a type over a range (no fund code), paged at the bulk page size. */
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
                    null, language, null);

            String body = doPost(request);

            if (body != null && body.trim().startsWith("<")) {
                log.warn("TEFAS WAF block: {} {} ({} to {})", fundType, fundCode, startDate, endDate);
                throw new ExternalApiRequestException(
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

    @CircuitBreaker(name = "tefas")
    @Retry(name = "tefas-bulk")
    public List<TefasFundReturnsDto> fetchReturns(FundType fundType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fonTipi", fundType.name());
        payload.put("dil", language);
        payload.put("calismaTipi", 2);
        payload.put("donemGetiri1a", "1");
        payload.put("donemGetiri3a", "1");
        payload.put("donemGetiri6a", "1");
        payload.put("donemGetiriyb", "1");
        payload.put("donemGetiri1y", "1");
        payload.put("donemGetiri3y", "1");
        payload.put("donemGetiri5y", "1");
        return postAndParse(tefasReturnsPath, payload, "returns",
                new TypeReference<>() {});
    }

    @CircuitBreaker(name = "tefas")
    @Retry(name = "tefas-bulk")
    public List<TefasFundAllocationDto> fetchAllocations(FundType fundType, LocalDate date) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fonTipi", fundType.name());
        payload.put("basTarih", date.format(DATE_FORMAT));
        payload.put("bitTarih", date.format(DATE_FORMAT));
        payload.put("basSira", 1);
        payload.put("bitSira", bulkPageSize);
        payload.put("dil", language);
        return postAndParse(tefasAllocationPath, payload, "allocation",
                new TypeReference<>() {});
    }

    @CircuitBreaker(name = "tefas")
    @Retry(name = "tefas")
    public TefasFundInfoDto fetchInfo(FundType fundType, String fundCode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fonKodu", fundCode);
        payload.put("fonTipi", fundType.name());
        payload.put("basSira", 1);
        payload.put("bitSira", 1);
        payload.put("dil", language);
        List<TefasFundInfoDto> list = postAndParse(tefasInfoPath, payload, "info",
                new TypeReference<>() {});
        return list.isEmpty() ? null : list.getFirst();
    }

    @CircuitBreaker(name = "tefas")
    @Retry(name = "tefas")
    public TefasFundProfileDto fetchProfile(FundType fundType, String fundCode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fonKodu", fundCode);
        payload.put("fonTipi", fundType.name());
        payload.put("dil", language);
        List<TefasFundProfileDto> list = postAndParse(tefasProfilePath, payload, "profile",
                new TypeReference<>() {});
        return list.isEmpty() ? null : list.getFirst();
    }

    private <T> List<T> postAndParse(String path, Object payload, String label,
                                     TypeReference<TefasGenericResponse<T>> typeRef) {
        long started = System.currentTimeMillis();
        try {
            String body = webClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept", "*/*")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (body == null || body.isBlank()) {
                log.warn("TEFAS {} empty body, refreshing session", label);
                sessionManager.invalidate();
                throw new ExternalApiException("TEFAS", "Empty response from " + label);
            }
            if (body.trim().startsWith("<")) {
                log.warn("TEFAS {} WAF block", label);
                throw new ExternalApiRequestException("WAF blocked " + label + " request");
            }
            TefasGenericResponse<T> response = objectMapper.readValue(body, typeRef);
            if (response.errorCode() != null) {
                throw new ExternalApiException("TEFAS",
                        "TEFAS error " + response.errorCode() + ": " + response.errorMessage());
            }
            long elapsed = System.currentTimeMillis() - started;
            int size = response.resultList() == null ? 0 : response.resultList().size();
            log.info("[TIMING] TEFAS {} took {}ms ({} records)", label, elapsed, size);
            return response.resultList() == null ? List.of() : response.resultList();
        } catch (ExternalApiException | ExternalApiRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("TEFAS", "Request failed for " + label, e);
        }
    }

    private List<TefasFundDto> parseResponse(String body) {
        String trimmed = body.trim();
        try {
            TefasResponse response = objectMapper.readValue(trimmed, TefasResponse.class);
            if (response.errorCode() != null) {
                throw new ExternalApiException("TEFAS",
                        "TEFAS error " + response.errorCode() + ": " + response.errorMessage());
            }
            if (response.resultList() == null || response.resultList().isEmpty()) {
                if (response.errorMessage() != null && !response.errorMessage().isBlank()) {
                    log.debug("TEFAS no-data response: {}", response.errorMessage());
                }
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
