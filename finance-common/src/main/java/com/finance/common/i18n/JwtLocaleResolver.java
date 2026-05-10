package com.finance.common.i18n;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
        return claimLocale().orElseGet(() -> super.resolveLocale(request));
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
