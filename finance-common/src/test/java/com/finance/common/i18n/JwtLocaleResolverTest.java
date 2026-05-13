package com.finance.common.i18n;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtLocaleResolverTest {

    private static final List<Locale> SUPPORTED = List.of(
            Locale.forLanguageTag("tr"),
            Locale.forLanguageTag("en"));
    private static final Locale DEFAULT = Locale.forLanguageTag("tr");

    private final JwtLocaleResolver resolver = new JwtLocaleResolver(SUPPORTED, DEFAULT);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveLocale_picksJwtLocaleClaim_whenSupported() {
        JwtLocaleResolver jwtFirst = new JwtLocaleResolver(
                List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("tr")),
                Locale.forLanguageTag("ja"));
        authenticate(jwtWithLocale("en"));
        MockHttpServletRequest request = new MockHttpServletRequest();

        Locale result = jwtFirst.resolveLocale(request);

        assertThat(result).isEqualTo(Locale.forLanguageTag("en"));
    }

    @Test
    void resolveLocale_fallsBackToAcceptHeader_whenJwtClaimMissing() {
        authenticate(jwtWithLocale(null));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "tr");

        Locale result = resolver.resolveLocale(request);

        assertThat(result.getLanguage()).isEqualTo("tr");
    }

    @Test
    void resolveLocale_ignoresUnsupportedClaim_andFallsBackToAcceptHeader() {
        authenticate(jwtWithLocale("de"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "en");

        Locale result = resolver.resolveLocale(request);

        assertThat(result.getLanguage()).isEqualTo("en");
    }

    @Test
    void resolveLocale_returnsDefault_whenNoAuthAndNoHeader() {
        HttpServletRequest request = new MockHttpServletRequest();

        Locale result = resolver.resolveLocale(request);

        assertThat(result).isEqualTo(DEFAULT);
    }

    private static Jwt jwtWithLocale(String locale) {
        Map<String, Object> claims = locale == null
                ? Map.of("sub", "user-1")
                : Map.of("sub", "user-1", "locale", locale);
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"),
                claims);
    }

    private static void authenticate(Jwt jwt) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(jwt, "n/a", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
