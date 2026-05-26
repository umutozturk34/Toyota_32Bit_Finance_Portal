package com.finance.user.client;

import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.ExternalApiException;
import com.finance.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakAdminExceptionTranslatorTest {

    private KeycloakAdminExceptionTranslator translator;

    @BeforeEach
    void setUp() {
        translator = new KeycloakAdminExceptionTranslator();
    }

    @Test
    void translate_returnsResourceNotFound_whenStatusIs404() {
        WebClientResponseException ex = WebClientResponseException.create(
                HttpStatus.NOT_FOUND.value(), "Not Found", null, "{}".getBytes(), null);

        RuntimeException result = translator.translate("getUser", ex);

        assertThat(result).isInstanceOf(ResourceNotFoundException.class);
        assertThat(result.getMessage()).contains("getUser");
    }

    @ParameterizedTest
    @CsvSource({"500, 500", "502, 502", "503, 503"})
    void translate_returnsExternalApiException_when5xxFailure(int status, int expectedStatus) {
        WebClientResponseException ex = WebClientResponseException.create(
                status, "Err", null, "{}".getBytes(), null);

        RuntimeException result = translator.translate("updateEmail", ex);

        assertThat(result).isInstanceOf(ExternalApiException.class);
        assertThat(result.getMessage())
                .contains("updateEmail")
                .contains(String.valueOf(expectedStatus));
        assertThat(((ExternalApiException) result).getServiceName()).isEqualTo("KEYCLOAK");
    }

    @ParameterizedTest
    @CsvSource({"400", "401", "403"})
    void translate_returnsBadRequest_when4xxClientFailure(int status) {
        WebClientResponseException ex = WebClientResponseException.create(
                status, "Err", null, "{\"errorMessage\":\"invalid\"}".getBytes(), null);

        RuntimeException result = translator.translate("updateBasics", ex);

        assertThat(result).isInstanceOf(BadRequestException.class);
        assertThat(result.getMessage()).isEqualTo("error.keycloak.rejected");
    }

    @Test
    void translate_returnsBadRequestWithConflictKey_when409() {
        WebClientResponseException ex = WebClientResponseException.create(
                HttpStatus.CONFLICT.value(), "Conflict", null,
                "{\"errorMessage\":\"User exists with same username\"}".getBytes(), null);

        RuntimeException result = translator.translate("updateBasics", ex);

        assertThat(result).isInstanceOf(BadRequestException.class);
        assertThat(result.getMessage()).isEqualTo("error.keycloak.conflict");
    }

    @Test
    void translateNetwork_returnsExternalApiException_withNetworkPrefix() {
        WebClientRequestException ex = new WebClientRequestException(
                new UnknownHostException("kc.local"), HttpMethod.GET,
                URI.create("http://kc.local/x"), HttpHeaders.EMPTY);

        RuntimeException result = translator.translateNetwork("listUsers", ex);

        assertThat(result).isInstanceOf(ExternalApiException.class);
        assertThat(result.getMessage())
                .contains("listUsers")
                .contains("network failure")
                .contains("kc.local");
        assertThat(((ExternalApiException) result).getServiceName()).isEqualTo("KEYCLOAK");
    }
}
