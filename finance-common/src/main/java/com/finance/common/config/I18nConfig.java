package com.finance.common.config;

import com.finance.common.i18n.JwtLocaleResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;

import java.nio.charset.StandardCharsets;

/**
 * Wires the i18n stack: a UTF-8 {@code messages}/{@code validation-messages} bundle that does not
 * fall back to the system locale or echo the code on a miss, a {@link JwtLocaleResolver} honoring
 * the configured supported/default locales, and a bean-validation factory bound to the same
 * message source so constraint messages are localized.
 */
@Configuration
@EnableConfigurationProperties(I18nProperties.class)
@RequiredArgsConstructor
public class I18nConfig {

    private static final String[] BASENAMES = {
            "classpath:messages",
            "classpath:validation-messages"
    };

    private final I18nProperties properties;

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasenames(BASENAMES);
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setDefaultLocale(properties.defaultLocale());
        source.setUseCodeAsDefaultMessage(false);
        source.setFallbackToSystemLocale(false);
        return source;
    }

    @Bean
    public LocaleResolver localeResolver() {
        return new JwtLocaleResolver(properties.supportedLocales(), properties.defaultLocale());
    }

    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }
}
