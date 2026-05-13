package com.finance.user.client;

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
    @CsvSource({"400, 400", "401, 401", "403, 403", "500, 500", "503, 503"})
    void translate_returnsExternalApiException_whenStatusIsNot404(int status, int expectedStatus) {
        WebClientResponseException ex = WebClientResponseException.create(
                status, "Err", null, "{}".getBytes(), null);

        RuntimeException result = translator.translate("updateEmail", ex);

        assertThat(result).isInstanceOf(ExternalApiException.class);
        assertThat(result.getMessage())
                .contains("updateEmail")
                .contains(String.valueOf(expectedStatus));
        assertThat(((ExternalApiException) result).getServiceName()).isEqualTo("KEYCLOAK");
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
