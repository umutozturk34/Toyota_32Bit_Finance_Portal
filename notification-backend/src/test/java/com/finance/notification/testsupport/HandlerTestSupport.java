package com.finance.notification.testsupport;

import com.finance.common.i18n.Translator;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

public final class HandlerTestSupport {

    private HandlerTestSupport() {}

    public static Translator turkishTranslator() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages");
        source.setDefaultEncoding("UTF-8");
        LocaleContextHolder.setLocale(Locale.forLanguageTag("tr"));
        return new Translator(source);
    }

    public static Translator englishTranslator() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages");
        source.setDefaultEncoding("UTF-8");
        LocaleContextHolder.setLocale(Locale.forLanguageTag("en"));
        return new Translator(source);
    }

    public static void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }
}
