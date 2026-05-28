package com.finance.common.i18n;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Locale resolver that prefers a supported {@code Accept-Language} header, then falls back to the
 * authenticated JWT's {@code locale} claim (if supported), and finally the configured default.
 * Only locales in the supported list are ever returned.
 */
public class JwtLocaleResolver extends AcceptHeaderLocaleResolver {

    private final List<Locale> supportedLocales;
    private final Locale defaultLocale;

    public JwtLocaleResolver(List<Locale> supportedLocales, Locale defaultLocale) {
        this.supportedLocales = List.copyOf(supportedLocales);
        this.defaultLocale = defaultLocale;
        setDefaultLocale(defaultLocale);
        setSupportedLocales(this.supportedLocales);
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        Locale headerLocale = super.resolveLocale(request);
        if (headerLocale != null && supportedLocales.contains(headerLocale)) {
            return headerLocale;
        }
        return claimLocale().orElse(defaultLocale);
    }

    private Optional<Locale> claimLocale() {
        return jwtPrincipal()
                .map(jwt -> jwt.getClaimAsString("locale"))
                .filter(claim -> claim != null && !claim.isBlank())
                .map(Locale::forLanguageTag)
                .filter(supportedLocales::contains);
    }

    private static Optional<Jwt> jwtPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Optional.empty();
        Object principal = auth.getPrincipal();
        return principal instanceof Jwt jwt ? Optional.of(jwt) : Optional.empty();
    }
}
