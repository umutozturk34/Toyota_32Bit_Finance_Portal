package com.finance.common.filter;

import tools.jackson.databind.ObjectMapper;
import com.finance.common.config.AppProperties;
import com.finance.common.dto.ErrorResponse;
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
import java.util.List;
import java.util.function.Supplier;

@Log4j2
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final LettuceBasedProxyManager<String> proxyManager;
    private final List<RateLimitTier> tiers;
    private final com.finance.common.i18n.Translator translator;

    public RateLimitFilter(ObjectMapper objectMapper,
                           AppProperties appProperties,
                           StatefulRedisConnection<String, byte[]> connection,
                           List<RateLimitTier> tiers,
                           com.finance.common.i18n.Translator translator) {
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.tiers = tiers;
        this.translator = translator;
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
        RateLimitTier tier = resolveTier(path, method);

        String userId = resolveUserId();
        String bucketKey = "rate-limit:" + userId + ":" + tier.name();

        Supplier<BucketConfiguration> configSupplier = () -> createBucketConfiguration(tier);
        ConsumptionProbe probe = proxyManager.builder()
                .build(bucketKey, configSupplier)
                .tryConsumeAndReturnRemaining(1);

        log.debug("[RateLimit] user={} tier={} path={} remaining={} consumed={}",
                userId, tier.name(), path, probe.getRemainingTokens(), probe.isConsumed());

        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);

            log.warn("Rate limit exceeded for user={} tier={} path={} retryAfter={}s",
                    userId, tier.name(), path, retryAfterSeconds);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("X-Rate-Limit-Remaining", "0");
            response.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(retryAfterSeconds));

            String localizedMessage = translator.translateOrSelf(tier.errorMessage());
            ErrorResponse error = ErrorResponse.of(localizedMessage, tier.errorCode(), path);
            response.getWriter().write(objectMapper.writeValueAsString(error));
        }
    }

    private RateLimitTier resolveTier(String path, String method) {
        return tiers.stream()
                .filter(t -> t.matches(path, method))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No rate limit tier matched for " + method + " " + path));
    }

    private BucketConfiguration createBucketConfiguration(RateLimitTier tier) {
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
