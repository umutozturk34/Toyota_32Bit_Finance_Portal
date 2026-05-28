package com.finance.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Stamps the response with a {@code Content-Language} header (from the resolved request locale)
 * after the chain runs, unless a downstream handler already set one.
 */
@Component
public class ContentLanguageFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (!response.containsHeader(HttpHeaders.CONTENT_LANGUAGE)) {
                Locale locale = LocaleContextHolder.getLocale();
                response.setHeader(HttpHeaders.CONTENT_LANGUAGE, locale.toLanguageTag());
            }
        }
    }
}
