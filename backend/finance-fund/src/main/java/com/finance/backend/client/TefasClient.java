package com.finance.backend.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.dto.external.TefasFundDto;
import com.finance.backend.dto.internal.TefasResponse;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.mapper.FundMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
public class TefasClient {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Safari/605.1.15";
    private static final String TEFAS_BASE = "https://www.tefas.gov.tr";
    private static final TefasResponse EMPTY_RESPONSE = new TefasResponse(0, 0, 0, List.of());

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final FundMapper fundMapper;
    private final String tefasUrl;

    private volatile String sessionCookie;

    public TefasClient(RestTemplate restTemplate,
                       FundMapper fundMapper,
                       @Value("${app.tefas-url}") String tefasUrl) {
        this.restTemplate = restTemplate;
        this.fundMapper = fundMapper;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.tefasUrl = tefasUrl;
    }

    private HttpHeaders createBaseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", USER_AGENT);
        headers.set("Accept-Encoding", "gzip, deflate, br");
        headers.set("Connection", "keep-alive");
        return headers;
    }

    private synchronized void refreshSession() {
        try {
            HttpHeaders headers = createBaseHeaders();
            headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

            ResponseEntity<String> response = restTemplate.exchange(
                    TEFAS_BASE + "/TarihselVeriler.aspx",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);

            List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
            if (cookies != null && !cookies.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String c : cookies) {
                    String cookiePart = c.split(";")[0];
                    if (!sb.isEmpty()) sb.append("; ");
                    sb.append(cookiePart);
                }
                sessionCookie = sb.toString();
                log.info("TEFAS session refreshed: {} chars", sessionCookie.length());
            } else {
                log.warn("TEFAS session refresh returned no cookies");
            }
        } catch (Exception e) {
            log.warn("Failed to refresh TEFAS session: {}", e.getMessage());
        }
    }

    private void ensureSession() {
        if (sessionCookie == null) {
            refreshSession();
        }
    }

    public List<TefasFundDto> fetchAllByfFunds(LocalDate date) {
        String dateStr = date.format(DATE_FORMAT);
        return fundMapper.toDto(fetchFunds("BYF", null, dateStr, dateStr).data());
    }

    public List<TefasFundDto> fetchFundHistory(String fundType, String fundCode, LocalDate startDate, LocalDate endDate) {
        return fundMapper.toDto(fetchFunds(fundType, fundCode, startDate.format(DATE_FORMAT), endDate.format(DATE_FORMAT)).data());
    }

    private HttpEntity<MultiValueMap<String, String>> buildRequest(String fundType, String fundCode, String startDate, String endDate) {
        HttpHeaders headers = createBaseHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Accept", "*/*");
        if (sessionCookie != null) {
            headers.set("Cookie", sessionCookie);
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("fontip", fundType);
        body.add("bastarih", startDate);
        body.add("bittarih", endDate);
        if (fundCode != null && !fundCode.isBlank()) {
            body.add("fonkod", fundCode);
        }

        return new HttpEntity<>(body, headers);
    }

    private TefasResponse parseResponse(String responseBody, String fundType, String fundCode) throws Exception {
        TefasResponse tefasResponse = objectMapper.readValue(responseBody, TefasResponse.class);
        log.debug("TEFAS response: type={}, code={}, records={}", fundType, fundCode, tefasResponse.recordsTotal());
        return tefasResponse.recordsTotal() == 0 ? EMPTY_RESPONSE : tefasResponse;
    }

    private TefasResponse handleResponseBody(ResponseEntity<String> response, String fundType, String fundCode) throws Exception {
        if (response.getBody() == null || response.getBody().isBlank()) {
            return null;
        }
        String responseBody = response.getBody().trim();
        if (responseBody.startsWith("<")) {
            return null;
        }
        return parseResponse(responseBody, fundType, fundCode);
    }

    private TefasResponse fetchFunds(String fundType, String fundCode, String startDate, String endDate) {
        ensureSession();
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    tefasUrl, buildRequest(fundType, fundCode, startDate, endDate), String.class);

            TefasResponse result = handleResponseBody(response, fundType, fundCode);
            if (result != null) {
                return result;
            }

            if (response.getBody() == null || response.getBody().isBlank()) {
                throw new ExternalApiException("TEFAS", "Empty response for " + fundType + " " + (fundCode != null ? fundCode : "all"));
            }

            log.debug("TEFAS returned HTML for {} {} ({} - {}), refreshing session and retrying",
                    fundType, fundCode, startDate, endDate);
            refreshSession();
            return retryFetchFunds(fundType, fundCode, startDate, endDate);
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("TEFAS",
                    "Failed to fetch " + fundType + " " + (fundCode != null ? fundCode : "all") + ": " + e.getMessage(), e);
        }
    }

    private TefasResponse retryFetchFunds(String fundType, String fundCode, String startDate, String endDate) {
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    tefasUrl, buildRequest(fundType, fundCode, startDate, endDate), String.class);

            TefasResponse result = handleResponseBody(response, fundType, fundCode);
            if (result != null) {
                return result;
            }

            if (response.getBody() != null && !response.getBody().isBlank()) {
                log.warn("TEFAS still returning HTML after session refresh for {} {} ({} - {})",
                        fundType, fundCode, startDate, endDate);
            }
            return EMPTY_RESPONSE;
        } catch (Exception e) {
            log.warn("TEFAS retry failed for {} {}: {}", fundType, fundCode, e.getMessage(), e);
            return EMPTY_RESPONSE;
        }
    }
}
