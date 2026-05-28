package com.finance.user.client;

import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.ExternalApiException;
import com.finance.common.exception.ResourceNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Maps WebClient errors from the Keycloak Admin API onto the app's domain exceptions: 404 to
 * not-found, 409/other 4xx to bad-request, and 5xx or network failures to external-API errors.
 */
@Log4j2
@Component
public class KeycloakAdminExceptionTranslator {

    private static final String SERVICE = "KEYCLOAK";

    /** Translates an HTTP error response; 401 is handled by the caller's retry path and never reaches here. */
    public RuntimeException translate(String operation, WebClientResponseException ex) {
        if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
            return new ResourceNotFoundException("Keycloak resource not found for " + operation);
        }
        if (ex.getStatusCode() == HttpStatus.CONFLICT) {
            log.warn("Keycloak {} conflict: body={}", operation, ex.getResponseBodyAsString());
            return new BadRequestException("error.keycloak.conflict");
        }
        if (ex.getStatusCode().is4xxClientError()) {
            log.warn("Keycloak {} rejected: status={} body={}", operation, ex.getStatusCode(), ex.getResponseBodyAsString());
            return new BadRequestException("error.keycloak.rejected");
        }
        log.error("Keycloak {} failed: status={} body={}", operation, ex.getStatusCode(), ex.getResponseBodyAsString());
        return new ExternalApiException(SERVICE, operation + " failed with status " + ex.getStatusCode().value());
    }

    public RuntimeException translateNetwork(String operation, WebClientRequestException ex) {
        return new ExternalApiException(SERVICE, operation + " network failure: " + ex.getMessage());
    }
}
