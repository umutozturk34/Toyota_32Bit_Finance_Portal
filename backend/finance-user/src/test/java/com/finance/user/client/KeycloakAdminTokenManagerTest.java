package com.finance.user.client;

import com.finance.common.exception.ExternalApiException;
import com.finance.user.client.dto.TokenResponse;
import com.finance.user.config.KeycloakAdminProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakAdminTokenManagerTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private WebClient webClient;

    private KeycloakAdminProperties properties;
    private KeycloakAdminTokenManager manager;

    @BeforeEach
    void setUp() {
        properties = new KeycloakAdminProperties();
        properties.setAdminUser("admin");
        properties.setAdminPassword("secret");
        properties.setTokenSafetyMarginSeconds(10);
        manager = new KeycloakAdminTokenManager(webClient, properties);
    }

    @Test
    void getToken_fetchesAndReturnsNewToken_whenNoSnapshot() {
        stubFetchReturning(new TokenResponse("access-1", 300, "Bearer"));

        String token = manager.getToken();

        assertThat(token).isEqualTo("access-1");
    }

    @Test
    void getToken_returnsCachedValue_whenSnapshotStillValid() {
        stubFetchReturning(new TokenResponse("first", 300, "Bearer"));
        String firstCall = manager.getToken();
        stubFetchReturning(new TokenResponse("ignored", 300, "Bearer"));

        String secondCall = manager.getToken();

        assertThat(firstCall).isEqualTo("first");
        assertThat(secondCall).isEqualTo("first");
    }

    @Test
    void getToken_refetches_whenInvalidated() {
        stubFetchReturning(new TokenResponse("first", 300, "Bearer"));
        manager.getToken();
        stubFetchReturning(new TokenResponse("second", 300, "Bearer"));
        manager.invalidate();

        String token = manager.getToken();

        assertThat(token).isEqualTo("second");
    }

    @Test
    void getToken_refetches_whenSnapshotExpired() {
        stubFetchReturning(new TokenResponse("short-lived", 5, "Bearer"));
        manager.getToken();
        stubFetchReturning(new TokenResponse("renewed", 300, "Bearer"));

        String token = manager.getToken();

        assertThat(token).isEqualTo("renewed");
    }

    @Test
    void getToken_throwsExternalApiException_whenKeycloakReturnsErrorStatus() {
        WebClientResponseException ex = WebClientResponseException.create(
                HttpStatus.UNAUTHORIZED.value(), "Unauthorized", null, "{}".getBytes(), null);
        stubFetchThrowing(ex);

        assertThatThrownBy(() -> manager.getToken())
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("401")
                .extracting(t -> ((ExternalApiException) t).getServiceName())
                .isEqualTo("KEYCLOAK");
    }

    @Test
    void getToken_throwsExternalApiException_whenEndpointUnreachable() {
        WebClientRequestException ex = new WebClientRequestException(
                new UnknownHostException("keycloak"), HttpMethod.POST,
                URI.create("http://kc/x"), HttpHeaders.EMPTY);
        stubFetchThrowing(ex);

        assertThatThrownBy(() -> manager.getToken())
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("unreachable")
                .extracting(t -> ((ExternalApiException) t).getServiceName())
                .isEqualTo("KEYCLOAK");
    }

    private void stubFetchReturning(TokenResponse response) {
        when(webClient.post().uri(any(String.class))
                .contentType(any())
                .body(any())
                .retrieve()
                .bodyToMono(TokenResponse.class))
                .thenReturn(Mono.just(response));
    }

    private void stubFetchThrowing(RuntimeException ex) {
        when(webClient.post().uri(any(String.class))
                .contentType(any())
                .body(any())
                .retrieve()
                .bodyToMono(TokenResponse.class))
                .thenThrow(ex);
    }
}
