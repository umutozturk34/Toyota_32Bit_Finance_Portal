package com.finance.shared.filter;
import io.github.resilience4j.ratelimiter.RateLimiter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * WebClient filter that throttles outbound calls through a Resilience4j {@link RateLimiter},
 * protecting third-party APIs from being exceeded. The blocking permit wait is offloaded to the
 * bounded-elastic scheduler so the reactive event loop is never stalled.
 */
public class RateLimiterFilter implements ExchangeFilterFunction {
    private final RateLimiter rateLimiter;

    public RateLimiterFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return Mono.fromRunnable(() -> RateLimiter.waitForPermission(rateLimiter))
                .subscribeOn(Schedulers.boundedElastic())
                .then(next.exchange(request));
    }
}
