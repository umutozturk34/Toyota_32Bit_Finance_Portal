package com.finance.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;

/**
 * Locale settings under {@code app.i18n}. Defaults to Turkish with Turkish/English support when
 * unspecified; the supported-locale list is defensively copied so the record stays immutable.
 */
@ConfigurationProperties(prefix = "app.i18n")
public record I18nProperties(Locale defaultLocale, List<Locale> supportedLocales) {

    public I18nProperties {
        defaultLocale = defaultLocale == null ? Locale.forLanguageTag("tr") : defaultLocale;
        supportedLocales = (supportedLocales == null || supportedLocales.isEmpty())
                ? List.of(Locale.forLanguageTag("tr"), Locale.forLanguageTag("en"))
                : List.copyOf(supportedLocales);
    }
}
