package com.finance.common.filter;

import com.finance.common.config.AppProperties;
import com.finance.common.i18n.Translator;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.api.StatefulRedisConnection;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private Translator translator;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        StatefulRedisConnection<String, byte[]> connection = mock(StatefulRedisConnection.class, RETURNS_DEEP_STUBS);
        translator = mock(Translator.class);
        RateLimitTier publicTier = new TestTier("PUBLIC", "/api/public", "GET");
        RateLimitTier adminTier = new TestTier("ADMIN", "/api/admin", "POST");
        filter = new RateLimitFilter(new ObjectMapper(), new AppProperties(), connection,
                List.of(adminTier, publicTier), translator);
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNotFilter_returnsTrue_forNonApiPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");

        boolean skip = filter.shouldNotFilter(request);

        assertThat(skip).isTrue();
    }

    @Test
    void shouldNotFilter_returnsFalse_forApiPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/stocks");

        boolean skip = filter.shouldNotFilter(request);

        assertThat(skip).isFalse();
    }

    @Test
    void resolveTier_returnsMatchingTier_byPathAndMethod() throws Exception {
        Method m = RateLimitFilter.class.getDeclaredMethod("resolveTier", String.class, String.class);
        m.setAccessible(true);

        RateLimitTier tier = (RateLimitTier) m.invoke(filter, "/api/public", "GET");

        assertThat(tier.name()).isEqualTo("PUBLIC");
    }

    @Test
    void resolveTier_raises_whenNoTierMatches() throws Exception {
        Method m = RateLimitFilter.class.getDeclaredMethod("resolveTier", String.class, String.class);
        m.setAccessible(true);

        assertThatThrownBy(() -> m.invoke(filter, "/api/unknown", "DELETE"))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void resolveUserId_returnsJwtSubject_whenAuthenticated() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-42")
                .claim("sub", "user-42").issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofMinutes(5))).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, null, List.of()));

        Method m = RateLimitFilter.class.getDeclaredMethod("resolveUserId");
        m.setAccessible(true);
        String userId = (String) m.invoke(filter);

        assertThat(userId).isEqualTo("user-42");
    }

    @Test
    void resolveUserId_returnsAnonymous_whenNoAuthentication() throws Exception {
        Method m = RateLimitFilter.class.getDeclaredMethod("resolveUserId");
        m.setAccessible(true);

        String userId = (String) m.invoke(filter);

        assertThat(userId).isEqualTo("anonymous");
    }

    @Test
    void resolveUserId_returnsAnonymous_whenPrincipalNotJwt() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("plain-username", null, List.of()));

        Method m = RateLimitFilter.class.getDeclaredMethod("resolveUserId");
        m.setAccessible(true);
        String userId = (String) m.invoke(filter);

        assertThat(userId).isEqualTo("anonymous");
    }

    @Test
    void doFilterInternal_forwardsRequestAndSetsRemainingHeader_whenTokenConsumed() throws Exception {
        injectProxyManagerReturning(ConsumptionProbe.consumed(7, 0));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getHeader("X-Rate-Limit-Remaining")).isEqualTo("7");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void doFilterInternal_writes429WithRetryAfterHeaders_whenRateLimitExceeded() throws Exception {
        when(translator.translateOrSelf("rate.limit.PUBLIC")).thenReturn("too many requests");
        injectProxyManagerReturning(ConsumptionProbe.rejected(0, 5_000_000_000L, 5_000_000_000L));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getHeader("X-Rate-Limit-Remaining")).isEqualTo("0");
        assertThat(response.getHeader("X-Rate-Limit-Retry-After-Seconds")).isEqualTo("5");
        assertThat(response.getContentAsString())
                .contains("too many requests")
                .contains("RATE_LIMIT_PUBLIC");
    }

    @SuppressWarnings("unchecked")
    private void injectProxyManagerReturning(ConsumptionProbe probe) throws Exception {
        BucketProxy bucketProxy = mock(BucketProxy.class);
        when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

        RemoteBucketBuilder<String> builder = mock(RemoteBucketBuilder.class);
        when(builder.build(any(String.class), any(Supplier.class))).thenReturn(bucketProxy);

        LettuceBasedProxyManager<String> proxyManager = mock(LettuceBasedProxyManager.class);
        when(proxyManager.builder()).thenReturn(builder);

        Field field = RateLimitFilter.class.getDeclaredField("proxyManager");
        field.setAccessible(true);
        field.set(filter, proxyManager);
    }

    private static class TestTier implements RateLimitTier {
        private final String name;
        private final String path;
        private final String method;

        TestTier(String name, String path, String method) {
            this.name = name;
            this.path = path;
            this.method = method;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean matches(String requestPath, String requestMethod) {
            return requestPath.startsWith(path) && method.equals(requestMethod);
        }

        @Override
        public Bandwidth toBandwidth(AppProperties.RateLimit rl) {
            return Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofMinutes(1)).build();
        }

        @Override
        public String errorCode() {
            return "RATE_LIMIT_" + name;
        }

        @Override
        public String errorMessage() {
            return "rate.limit." + name;
        }
    }
}
