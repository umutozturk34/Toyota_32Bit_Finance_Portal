package com.finance.notification.core.dispatch.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakUserEmailLookupTest {

    private KeycloakUserEmailLookup lookup;

    @BeforeEach
    void setUp() throws Exception {
        lookup = new KeycloakUserEmailLookup("http://kc", "finance", "admin", "pw",
                new com.finance.notification.config.NotificationCacheProperties(50_000L, 64L, null, null, null, null));
        injectWebClient(stubClient(req -> tokenAndUserResponse(req)));
    }

    private WebClient stubClient(ExchangeFunction exchange) {
        return WebClient.builder().exchangeFunction(exchange).build();
    }

    private Mono<ClientResponse> tokenAndUserResponse(org.springframework.web.reactive.function.client.ClientRequest req) {
        String uri = req.url().toString();
        if (uri.contains("/token")) {
            return Mono.just(jsonResponse("{\"access_token\":\"abc\",\"expires_in\":300}"));
        }
        if (uri.contains("/users/")) {
            return Mono.just(jsonResponse(
                    "{\"id\":\"user-1\",\"username\":\"alice\",\"email\":\"alice@example.com\","
                            + "\"firstName\":\"Alice\",\"lastName\":\"X\"}"));
        }
        if (uri.contains("/users?")) {
            return Mono.just(jsonResponse(
                    "[{\"id\":\"u1\",\"username\":\"a\",\"email\":\"a@x.com\"},"
                            + "{\"id\":\"u2\",\"username\":\"b\",\"email\":\"b@x.com\"}]"));
        }
        return Mono.just(jsonResponse("{}"));
    }

    private ClientResponse jsonResponse(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private void injectWebClient(WebClient client) throws Exception {
        Field field = KeycloakUserEmailLookup.class.getDeclaredField("webClient");
        field.setAccessible(true);
        field.set(lookup, client);
    }

    @Test
    void findEmail_returnsEmail_fromProfileLookup() {
        Optional<String> result = lookup.findEmail("user-1");

        assertThat(result).contains("alice@example.com");
    }

    @Test
    void findEmail_returnsEmptyOptional_whenEmailBlank() throws Exception {
        injectWebClient(stubClient(req -> {
            if (req.url().toString().contains("/token")) {
                return Mono.just(jsonResponse("{\"access_token\":\"abc\",\"expires_in\":300}"));
            }
            return Mono.just(jsonResponse("{\"id\":\"u\",\"username\":\"x\",\"email\":\"\"}"));
        }));

        Optional<String> result = lookup.findEmail("user-1");

        assertThat(result).isEmpty();
    }

    @Test
    void findProfile_cachesResult_acrossRepeatedCalls() {
        Optional<KeycloakUserProfile> first = lookup.findProfile("user-1");
        Optional<KeycloakUserProfile> second = lookup.findProfile("user-1");

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(second.get().email()).isEqualTo("alice@example.com");
    }

    @Test
    void findProfile_returnsEmpty_whenUpstreamFails() throws Exception {
        injectWebClient(stubClient(req -> Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build())));

        Optional<KeycloakUserProfile> result = lookup.findProfile("user-1");

        assertThat(result).isEmpty();
    }

    @Test
    void search_returnsEmpty_whenQueryBlank() {
        List<KeycloakUserProfile> result = lookup.search("   ", 10);

        assertThat(result).isEmpty();
    }

    @Test
    void search_returnsEmpty_whenQueryNull() {
        List<KeycloakUserProfile> result = lookup.search(null, 10);

        assertThat(result).isEmpty();
    }

    @Test
    void search_returnsProfiles_fromUpstreamResponse() throws Exception {
        injectWebClient(stubClient(req -> {
            String uri = req.url().toString();
            if (uri.contains("/token")) {
                return Mono.just(jsonResponse("{\"access_token\":\"abc\",\"expires_in\":300}"));
            }
            return Mono.just(jsonResponse(
                    "[{\"id\":\"u1\",\"username\":\"a\",\"email\":\"a@x.com\"},"
                            + "{\"id\":\"u2\",\"username\":\"b\",\"email\":\"b@x.com\"}]"));
        }));

        List<KeycloakUserProfile> result = lookup.search("alice", 10);

        assertThat(result).hasSize(2);
    }

    @Test
    void search_returnsEmpty_whenUpstreamFails() throws Exception {
        injectWebClient(stubClient(req -> Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build())));

        List<KeycloakUserProfile> result = lookup.search("alice", 10);

        assertThat(result).isEmpty();
    }
}
