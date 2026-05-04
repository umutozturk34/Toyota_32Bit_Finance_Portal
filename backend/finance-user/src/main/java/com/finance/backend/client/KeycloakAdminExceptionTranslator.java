package com.finance.backend.client;

import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.exception.ResourceNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Log4j2
@Component
public class KeycloakAdminExceptionTranslator {

    private static final String SERVICE = "KEYCLOAK";

    public RuntimeException translate(String operation, WebClientResponseException ex) {
        if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
            return new ResourceNotFoundException("Keycloak resource not found for " + operation);
        }
        log.error("Keycloak {} failed: status={} body={}", operation, ex.getStatusCode(), ex.getResponseBodyAsString());
        return new ExternalApiException(SERVICE, operation + " failed with status " + ex.getStatusCode().value());
    }

    public RuntimeException translateNetwork(String operation, WebClientRequestException ex) {
        return new ExternalApiException(SERVICE, operation + " network failure: " + ex.getMessage());
    }
}
