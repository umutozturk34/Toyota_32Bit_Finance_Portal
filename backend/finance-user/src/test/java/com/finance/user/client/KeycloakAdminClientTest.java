package com.finance.user.client;

import com.finance.common.exception.ExternalApiException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.user.config.KeycloakAdminProperties;
import com.finance.user.dto.KeycloakUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakAdminClientTest {

    private static final String USER_ID = "kc-user-1";

    @Mock private WebClient webClient;
    @Mock private WebClient.RequestHeadersUriSpec<?> getUriSpec;
    @Mock private WebClient.RequestBodyUriSpec putUriSpec;
    @Mock private WebClient.RequestHeadersUriSpec<?> deleteUriSpec;
    @Mock private WebClient.RequestHeadersSpec<?> getHeadersSpec;
    @Mock private WebClient.RequestBodySpec putBodySpec;
    @Mock private WebClient.RequestHeadersSpec<?> putHeadersSpec;
    @Mock private WebClient.RequestHeadersSpec<?> deleteHeadersSpec;
    @Mock private WebClient.ResponseSpec getResponseSpec;
    @Mock private WebClient.ResponseSpec putResponseSpec;
    @Mock private WebClient.ResponseSpec deleteResponseSpec;
    @Mock private KeycloakAdminTokenManager tokenManager;
    @Mock private KeycloakAdminExceptionTranslator translator;

    private KeycloakAdminClient client;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @BeforeEach
    void setUp() {
        KeycloakAdminProperties properties = new KeycloakAdminProperties();
        properties.setRealm("finance-realm");
        client = new KeycloakAdminClient(webClient, properties, tokenManager, translator);
        lenient().when(tokenManager.getToken()).thenReturn("token-123");

        lenient().when(webClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) getUriSpec);
        lenient().when(getUriSpec.uri(any(Function.class))).thenReturn((WebClient.RequestHeadersSpec) getHeadersSpec);
        lenient().when(getUriSpec.uri(any(String.class), any(Object[].class))).thenReturn((WebClient.RequestHeadersSpec) getHeadersSpec);
        lenient().when(getHeadersSpec.header(any(String.class), any(String[].class))).thenReturn((WebClient.RequestHeadersSpec) getHeadersSpec);
        lenient().when(getHeadersSpec.retrieve()).thenReturn(getResponseSpec);

        lenient().when(webClient.put()).thenReturn(putUriSpec);
        lenient().when(putUriSpec.uri(any(String.class), any(Object[].class))).thenReturn(putBodySpec);
        lenient().when(putUriSpec.uri(any(Function.class))).thenReturn(putBodySpec);
        lenient().when(putBodySpec.header(any(String.class), any(String[].class))).thenReturn(putBodySpec);
        lenient().when(putBodySpec.contentType(any())).thenReturn(putBodySpec);
        lenient().when(putBodySpec.bodyValue(any())).thenReturn((WebClient.RequestHeadersSpec) putHeadersSpec);
        lenient().when(putHeadersSpec.retrieve()).thenReturn(putResponseSpec);
        lenient().when(putResponseSpec.toBodilessEntity()).thenReturn(Mono.empty());

        lenient().when(webClient.delete()).thenReturn((WebClient.RequestHeadersUriSpec) deleteUriSpec);
        lenient().when(deleteUriSpec.uri(any(String.class), any(Object[].class))).thenReturn((WebClient.RequestHeadersSpec) deleteHeadersSpec);
        lenient().when(deleteHeadersSpec.header(any(String.class), any(String[].class))).thenReturn((WebClient.RequestHeadersSpec) deleteHeadersSpec);
        lenient().when(deleteHeadersSpec.retrieve()).thenReturn(deleteResponseSpec);
        lenient().when(deleteResponseSpec.toBodilessEntity()).thenReturn(Mono.empty());
    }

    @Test
    void listUsers_returnsCollectedFlux_onHappyPath() {
        KeycloakUser u = new KeycloakUser("id-1", "ali", "ali@x.com", "Ali", "Yilmaz", true, 0L);
        when(getResponseSpec.bodyToFlux(KeycloakUser.class)).thenReturn(Flux.just(u));

        List<KeycloakUser> result = client.listUsers(0, 20, "ali");

        assertThat(result).containsExactly(u);
    }

    @Test
    void listUsers_skipsSearchParam_whenSearchIsBlank() {
        when(getResponseSpec.bodyToFlux(KeycloakUser.class)).thenReturn(Flux.empty());

        List<KeycloakUser> result = client.listUsers(0, 20, "");

        assertThat(result).isEmpty();
    }

    @Test
    void countUsers_returnsTotal_whenBodyPresent() {
        when(getResponseSpec.bodyToMono(Long.class)).thenReturn(Mono.just(42L));

        long total = client.countUsers("ali");

        assertThat(total).isEqualTo(42L);
    }

    @Test
    void countUsers_returnsZero_whenBodyNull() {
        when(getResponseSpec.bodyToMono(Long.class)).thenReturn(Mono.empty());

        long total = client.countUsers(null);

        assertThat(total).isZero();
    }

    @Test
    void getEmail_returnsEmail_whenFetchedUserHasEmail() {
        stubFetchUser(Map.of("id", USER_ID, "email", "alice@x.com"));

        String email = client.getEmail(USER_ID);

        assertThat(email).isEqualTo("alice@x.com");
    }

    @Test
    void getEmail_returnsNull_whenFetchedUserHasNoEmail() {
        stubFetchUser(Map.of("id", USER_ID));

        String email = client.getEmail(USER_ID);

        assertThat(email).isNull();
    }

    @Test
    void fetchUser_throwsResourceNotFound_whenBodyEmpty() {
        when(getResponseSpec.bodyToMono(Map.class)).thenReturn(Mono.empty());

        assertThatThrownBy(() -> client.getEmail(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Keycloak user not found");
    }

    @Test
    void setEnabled_putsMergedBodyWithEnabledFlag() {
        stubFetchUser(Map.of("id", USER_ID, "email", "x@y.com", "enabled", false));

        client.setEnabled(USER_ID, true);

        Map<String, Object> body = capturePutBody();
        assertThat(body.get("enabled")).isEqualTo(true);
        assertThat(body.get("email")).isEqualTo("x@y.com");
    }

    @Test
    void setEmail_putsMergedBodyWithNewEmailAndVerifiedTrue() {
        stubFetchUser(Map.of("id", USER_ID, "email", "old@x.com"));

        client.setEmail(USER_ID, "new@y.com");

        Map<String, Object> body = capturePutBody();
        assertThat(body.get("email")).isEqualTo("new@y.com");
        assertThat(body.get("emailVerified")).isEqualTo(true);
    }

    @Test
    void setUserAttribute_writesAttributeUnderAttributesMap_whenNoExistingMap() {
        stubFetchUser(Map.of("id", USER_ID));

        client.setUserAttribute(USER_ID, "locale", "tr");

        @SuppressWarnings("unchecked")
        Map<String, List<String>> attributes = (Map<String, List<String>>) capturePutBody().get("attributes");
        assertThat(attributes).containsEntry("locale", List.of("tr"));
    }

    @Test
    void setUserAttribute_mergesIntoExistingAttributes_whenMapPresent() {
        stubFetchUser(Map.of(
                "id", USER_ID,
                "attributes", Map.of("themePreference", List.of("DARK"))));

        client.setUserAttribute(USER_ID, "locale", "en");

        @SuppressWarnings("unchecked")
        Map<String, List<String>> attributes = (Map<String, List<String>>) capturePutBody().get("attributes");
        assertThat(attributes).containsEntry("locale", List.of("en"));
        assertThat(attributes).containsEntry("themePreference", List.of("DARK"));
    }

    @Test
    void listCredentials_returnsList_whenCredentialsExist() {
        Map<String, Object> cred = Map.of("type", "otp", "id", "c-1");
        when(getResponseSpec.bodyToFlux(Map.class)).thenReturn(Flux.just(cred));

        List<Map<String, Object>> result = client.listCredentials(USER_ID);

        assertThat(result).containsExactly(cred);
    }

    @Test
    void listCredentials_returnsEmpty_whenBodyEmpty() {
        when(getResponseSpec.bodyToFlux(Map.class)).thenReturn(Flux.empty());

        List<Map<String, Object>> result = client.listCredentials(USER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void deleteCredential_invokesDeleteEndpoint() {
        client.deleteCredential(USER_ID, "cred-1");

        verify(webClient).delete();
        verify(deleteResponseSpec).toBodilessEntity();
    }

    @Test
    void sendActionsEmail_invokesPutEndpointOnce() {
        client.sendActionsEmail(USER_ID, List.of("UPDATE_PASSWORD"), "client", "https://app/x", 300L);

        verify(webClient, times(1)).put();
    }

    @Test
    void executeWithRetry_refreshesTokenAndRetries_when401Returned() {
        WebClientResponseException unauthorized = WebClientResponseException.create(
                HttpStatus.UNAUTHORIZED.value(), "Unauthorized", null, "{}".getBytes(), null);
        when(getResponseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.error(unauthorized))
                .thenReturn(Mono.just(Map.of("id", USER_ID, "email", "ok@x.com")));

        String email = client.getEmail(USER_ID);

        assertThat(email).isEqualTo("ok@x.com");
        verify(tokenManager).invalidate();
        verify(tokenManager, times(2)).getToken();
    }

    @Test
    void executeWithRetry_callsTranslator_whenNon401ResponseException() {
        WebClientResponseException forbidden = WebClientResponseException.create(
                HttpStatus.FORBIDDEN.value(), "Forbidden", null, "{}".getBytes(), null);
        when(getResponseSpec.bodyToMono(Map.class)).thenReturn(Mono.error(forbidden));
        when(translator.translate(any(), any())).thenReturn(new ExternalApiException("KEYCLOAK", "denied"));

        assertThatThrownBy(() -> client.getEmail(USER_ID))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("denied");
    }

    @Test
    void executeWithRetry_callsTranslatorNetwork_whenRequestExceptionEmitted() {
        WebClientRequestException reqEx = new WebClientRequestException(
                new UnknownHostException("kc"), HttpMethod.GET,
                URI.create("http://kc/x"), HttpHeaders.EMPTY);
        when(getResponseSpec.bodyToMono(Map.class)).thenReturn(Mono.error(reqEx));
        when(translator.translateNetwork(any(), any())).thenReturn(new ExternalApiException("KEYCLOAK", "net"));

        assertThatThrownBy(() -> client.getEmail(USER_ID))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("net");
    }

    @Test
    void retryOnce_callsTranslator_whenRetryFailsWithResponseException() {
        WebClientResponseException first = WebClientResponseException.create(
                HttpStatus.UNAUTHORIZED.value(), "Unauthorized", null, "{}".getBytes(), null);
        WebClientResponseException second = WebClientResponseException.create(
                HttpStatus.FORBIDDEN.value(), "Forbidden", null, "{}".getBytes(), null);
        when(getResponseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.error(first))
                .thenReturn(Mono.error(second));
        when(translator.translate(any(), any())).thenReturn(new ExternalApiException("KEYCLOAK", "still denied"));

        assertThatThrownBy(() -> client.getEmail(USER_ID))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("still denied");
        verify(tokenManager).invalidate();
    }

    @Test
    void retryOnce_callsTranslatorNetwork_whenRetryFailsWithRequestException() {
        WebClientResponseException unauthorized = WebClientResponseException.create(
                HttpStatus.UNAUTHORIZED.value(), "Unauthorized", null, "{}".getBytes(), null);
        WebClientRequestException reqEx = new WebClientRequestException(
                new UnknownHostException("kc"), HttpMethod.GET,
                URI.create("http://kc/x"), HttpHeaders.EMPTY);
        when(getResponseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.error(unauthorized))
                .thenReturn(Mono.error(reqEx));
        when(translator.translateNetwork(any(), any())).thenReturn(new ExternalApiException("KEYCLOAK", "net2"));

        assertThatThrownBy(() -> client.getEmail(USER_ID))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("net2");
    }

    private void stubFetchUser(Map<String, Object> user) {
        when(getResponseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(user));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePutBody() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(putBodySpec).bodyValue(captor.capture());
        return (Map<String, Object>) captor.getValue();
    }
}
