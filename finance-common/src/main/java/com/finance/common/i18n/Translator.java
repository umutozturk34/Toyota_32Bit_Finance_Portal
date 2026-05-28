package com.finance.common.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Thin facade over Spring's {@link MessageSource} that resolves messages against the current
 * request locale. String arguments that themselves look like message keys are recursively
 * translated before interpolation, so nested keys can be passed as parameters.
 */
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

    /**
     * Resolves {@code keyOrText} as a message key, returning the input itself (not an error) when no
     * such key exists; lets callers pass either an i18n key or an already-literal message.
     */
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

    /**
     * Heuristic: a message key is lowercase-initial, dot-separated and space-free, so ordinary
     * sentence arguments are not mistaken for keys.
     */
    private boolean looksLikeMessageKey(String s) {
        if (s.isEmpty() || s.indexOf('.') < 0 || s.indexOf(' ') >= 0) return false;
        char first = s.charAt(0);
        return first >= 'a' && first <= 'z';
    }
}
