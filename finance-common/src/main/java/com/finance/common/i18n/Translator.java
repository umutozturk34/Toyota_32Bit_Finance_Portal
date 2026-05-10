package com.finance.common.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class Translator {

    private final MessageSource messageSource;

    public String translate(String key, Object... args) {
        return messageSource.getMessage(key, resolveArgs(args, LocaleContextHolder.getLocale()),
                LocaleContextHolder.getLocale());
    }

    public String translate(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, resolveArgs(args, locale), locale);
    }

    public String translateOrSelf(String keyOrText, Object... args) {
        if (keyOrText == null) return null;
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(keyOrText, resolveArgs(args, locale), keyOrText, locale);
    }

    private Object[] resolveArgs(Object[] args, Locale locale) {
        if (args == null || args.length == 0) return args;
        Object[] resolved = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            resolved[i] = resolveArg(args[i], locale);
        }
        return resolved;
    }

    private Object resolveArg(Object arg, Locale locale) {
        if (!(arg instanceof String s)) return arg;
        if (!looksLikeMessageKey(s)) return arg;
        return messageSource.getMessage(s, null, s, locale);
    }

    private boolean looksLikeMessageKey(String s) {
        if (s.isEmpty() || s.indexOf('.') < 0 || s.indexOf(' ') >= 0) return false;
        char first = s.charAt(0);
        return first >= 'a' && first <= 'z';
    }
}
