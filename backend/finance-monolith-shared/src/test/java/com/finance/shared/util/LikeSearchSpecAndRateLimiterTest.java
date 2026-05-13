package com.finance.shared.util;

import com.finance.shared.filter.RateLimiterFilter;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeSearchSpecAndRateLimiterTest {

    @SuppressWarnings("unchecked")
    private final Root<Object> root = mock(Root.class);
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);

    @Test
    void byFieldsContains_varargs_buildsLowerLikePredicates_andOrsThem() {
        @SuppressWarnings("unchecked")
        Path<String> path = mock(Path.class);
        when(root.get(any(String.class))).thenReturn((Path) path);
        when(cb.lower(any())).thenReturn(path);
        Predicate likePredicate = mock(Predicate.class);
        when(cb.like(any(), any(String.class))).thenReturn(likePredicate);
        Predicate or = mock(Predicate.class);
        when(cb.or(any(Predicate[].class))).thenReturn(or);

        Predicate result = LikeSearchSpec.byFieldsContains(root, cb, "TERM", "name", "code");

        assertThat(result).isSameAs(or);
        verify(cb).or(any(Predicate[].class));
    }

    @Test
    void byFieldsContains_listOverload_emitsLowerCasePattern() {
        @SuppressWarnings("unchecked")
        Path<String> path = mock(Path.class);
        when(root.get(any(String.class))).thenReturn((Path) path);
        when(cb.lower(any())).thenReturn(path);
        Predicate likePredicate = mock(Predicate.class);
        when(cb.like(any(), any(String.class))).thenReturn(likePredicate);
        Predicate or = mock(Predicate.class);
        when(cb.or(any(Predicate[].class))).thenReturn(or);

        Predicate result = LikeSearchSpec.byFieldsContains(root, cb, "Foo", List.of("title"));

        assertThat(result).isSameAs(or);
    }

    @Test
    void rateLimiterFilter_invokesNextExchange_afterAcquiringPermit() {
        RateLimiter rateLimiter = RateLimiter.of("test", RateLimiterConfig.custom()
                .limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(1))
                .build());
        RateLimiterFilter filter = new RateLimiterFilter(rateLimiter);
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("http://example.com")).build();
        ExchangeFunction next = mock(ExchangeFunction.class);
        ClientResponse mockResponse = mock(ClientResponse.class);
        when(next.exchange(any())).thenReturn(Mono.just(mockResponse));

        ClientResponse result = filter.filter(request, next).block();

        assertThat(result).isSameAs(mockResponse);
        verify(next).exchange(request);
    }
}
