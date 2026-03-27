package com.finance.backend.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.api.StatefulRedisConnection;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;

@Log4j2
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final LettuceBasedProxyManager<String> proxyManager;

    public RateLimitFilter(ObjectMapper objectMapper,
                           AppProperties appProperties,
                           StatefulRedisConnection<String, byte[]> connection) {
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.proxyManager = Bucket4jLettuce.casBasedBuilder(connection)
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofHours(2))
                )
                .build();
    }

    private enum Tier {
        ADMIN_TRIGGER, ADMIN_READ, API
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        Tier tier = resolveTier(path, method);

        String userId = resolveUserId();
        String bucketKey = "rate-limit:" + userId + ":" + tier.name();

        Supplier<BucketConfiguration> configSupplier = () -> createBucketConfiguration(tier);
        ConsumptionProbe probe = proxyManager.builder()
                .build(bucketKey, configSupplier)
                .tryConsumeAndReturnRemaining(1);

        log.debug("[RateLimit] user={} tier={} path={} remaining={} consumed={}",
                userId, tier, path, probe.getRemainingTokens(), probe.isConsumed());

        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);
            TierError tierError = resolveTierError(tier);

            log.warn("Rate limit exceeded for user={} tier={} path={} retryAfter={}s",
                    userId, tier, path, retryAfterSeconds);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("X-Rate-Limit-Remaining", "0");
            response.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(retryAfterSeconds));

            ErrorResponse error = ErrorResponse.of(
            tierError.message(),
            tierError.code(),
                    path);
            response.getWriter().write(objectMapper.writeValueAsString(error));
        }
    }

    private Tier resolveTier(String path, String method) {
        if (path.startsWith("/api/v1/admin/trigger") && "POST".equalsIgnoreCase(method)) {
            return Tier.ADMIN_TRIGGER;
        }
        if (path.startsWith("/api/v1/admin")) {
            return Tier.ADMIN_READ;
        }
        return Tier.API;
    }

    private TierError resolveTierError(Tier tier) {
        return switch (tier) {
            case ADMIN_TRIGGER -> new TierError(
                    "RATE_LIMIT_ADMIN_TRIGGER_EXCEEDED",
                    "Admin güncelleme tetikleme sınırına ulaştın. Lütfen daha sonra tekrar dene.");
            case ADMIN_READ -> new TierError(
                    "RATE_LIMIT_ADMIN_READ_EXCEEDED",
                    "Admin okuma isteği sınırına ulaştın. Lütfen kısa bir süre sonra tekrar dene.");
            case API -> new TierError(
                    "RATE_LIMIT_API_EXCEEDED",
                    "API istek sınırına ulaştın. Lütfen biraz bekleyip tekrar dene.");
        };
    }

    private BucketConfiguration createBucketConfiguration(Tier tier) {
        AppProperties.RateLimit rl = appProperties.getRateLimit();
        Bandwidth bandwidth = switch (tier) {
            case ADMIN_TRIGGER -> Bandwidth.builder()
                    .capacity(rl.getAdminTriggerLimit())
                    .refillIntervally(rl.getAdminTriggerLimit(), Duration.ofHours(1))
                    .build();
            case ADMIN_READ -> Bandwidth.builder()
                    .capacity(rl.getAdminReadLimit())
                    .refillIntervally(rl.getAdminReadLimit(), Duration.ofMinutes(1))
                    .build();
            case API -> Bandwidth.builder()
                    .capacity(rl.getApiLimit())
                    .refillGreedy(rl.getApiLimit(), Duration.ofMinutes(1))
                    .build();
        };
        return BucketConfiguration.builder().addLimit(bandwidth).build();
    }

    private String resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return "anonymous";
    }

    private record TierError(String code, String message) {
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }
}
