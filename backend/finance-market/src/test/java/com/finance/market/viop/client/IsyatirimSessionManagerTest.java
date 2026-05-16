package com.finance.market.viop.client;

import com.finance.market.viop.config.ViopProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IsyatirimSessionManagerTest {

    private ViopProperties defaults() {
        return new ViopProperties(null, null, null, null, null, null, null, null,
                Duration.ofMinutes(30), Duration.ofSeconds(5));
    }

    private ExchangeFunction respondWithCookies(List<String> setCookies) {
        return request -> {
            ClientResponse.Builder builder = ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.TEXT_HTML_VALUE);
            for (String cookie : setCookies) {
                builder.header("Set-Cookie", cookie);
            }
            return Mono.just(builder.body("<html></html>").build());
        };
    }

    private ExchangeFunction respondWithoutCookies() {
        return request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.TEXT_HTML_VALUE)
                .body("<html></html>")
                .build());
    }

    private ExchangeFunction respondWithError() {
        return request -> Mono.error(new RuntimeException("network down"));
    }

    private WebClient client(ExchangeFunction exchange) {
        return WebClient.builder().baseUrl("https://example.com")
                .exchangeFunction(exchange)
                .build();
    }

    @Test
    void should_returnCookieHeader_when_setCookiesPresent() {
        WebClient webClient = client(respondWithCookies(List.of(
                "ASP.NET_SessionId=abc; Path=/; HttpOnly",
                "TS01abc=xyz; Path=/")));
        IsyatirimSessionManager manager = new IsyatirimSessionManager(webClient, defaults());

        String header = manager.currentCookieHeader();

        assertThat(header).contains("ASP.NET_SessionId=abc");
        assertThat(header).contains("TS01abc=xyz");
    }

    @Test
    void should_returnEmptyString_when_responseHasNoSetCookies() {
        WebClient webClient = client(respondWithoutCookies());
        IsyatirimSessionManager manager = new IsyatirimSessionManager(webClient, defaults());

        String header = manager.currentCookieHeader();

        assertThat(header).isEmpty();
    }

    @Test
    void should_returnEmptyString_when_upstreamThrows() {
        WebClient webClient = client(respondWithError());
        IsyatirimSessionManager manager = new IsyatirimSessionManager(webClient, defaults());

        String header = manager.currentCookieHeader();

        assertThat(header).isEmpty();
    }

    @Test
    void should_reuseCachedSession_when_calledBeforeTtlExpires() {
        java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
        ExchangeFunction counting = req -> {
            count.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Set-Cookie", "X=1; Path=/")
                    .body("<html></html>")
                    .build());
        };
        WebClient webClient = client(counting);
        IsyatirimSessionManager manager = new IsyatirimSessionManager(webClient, defaults());

        manager.currentCookieHeader();
        manager.currentCookieHeader();

        assertThat(count).hasValue(1);
    }

    @Test
    void should_clearAndRefresh_when_forceRefreshCalled() {
        java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
        ExchangeFunction counting = req -> {
            count.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Set-Cookie", "X=1; Path=/")
                    .body("<html></html>")
                    .build());
        };
        WebClient webClient = client(counting);
        IsyatirimSessionManager manager = new IsyatirimSessionManager(webClient, defaults());
        manager.currentCookieHeader();

        manager.forceRefresh();

        assertThat(count).hasValueGreaterThanOrEqualTo(2);
    }

    @Test
    void should_skipCookiesMissingEqualsSign_when_buildingHeader() {
        WebClient webClient = client(respondWithCookies(List.of(
                "valid=ok; Path=/",
                "INVALID;Path=/")));
        IsyatirimSessionManager manager = new IsyatirimSessionManager(webClient, defaults());

        String header = manager.currentCookieHeader();

        assertThat(header).contains("valid=ok");
        assertThat(header).doesNotContain("INVALID");
    }
}
