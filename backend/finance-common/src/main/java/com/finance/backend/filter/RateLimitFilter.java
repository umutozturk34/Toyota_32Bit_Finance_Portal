package com.finance.backend.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.ErrorResponse;
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

            log.warn("Rate limit exceeded for user={} tier={} path={} retryAfter={}s",
                    userId, tier, path, retryAfterSeconds);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("X-Rate-Limit-Remaining", "0");
            response.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(retryAfterSeconds));

            ErrorResponse error = ErrorResponse.of(tier.errorMessage(), tier.errorCode(), path);
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

    private BucketConfiguration createBucketConfiguration(Tier tier) {
        return BucketConfiguration.builder()
                .addLimit(tier.toBandwidth(appProperties.getRateLimit()))
                .build();
    }

    private String resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return "anonymous";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }
}
