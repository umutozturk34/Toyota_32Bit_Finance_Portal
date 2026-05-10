package com.finance.common.i18n;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslatorTest {

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private Translator translator;

    @ParameterizedTest
    @CsvSource({
            "tr, Merhaba",
            "en, Hello"
    })
    void translate_resolvesAgainstExplicitLocale(String tag, String expected) {
        Locale locale = Locale.forLanguageTag(tag);
        when(messageSource.getMessage(eq("greeting"), any(Object[].class), eq(locale))).thenReturn(expected);

        String result = translator.translate("greeting", locale);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void translate_fallsBackToContextHolderLocale_whenNoLocalePassed() {
        Locale previous = LocaleContextHolder.getLocale();
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        try {
            when(messageSource.getMessage(eq("greeting"), any(Object[].class), eq(Locale.ENGLISH))).thenReturn("Hello");

            String result = translator.translate("greeting");

            assertThat(result).isEqualTo("Hello");
        } finally {
            LocaleContextHolder.setLocale(previous);
        }
    }

    @Test
    void translate_passesArgumentsThrough() {
        when(messageSource.getMessage(eq("priceAlert"), any(Object[].class), eq(Locale.ENGLISH))).thenReturn("BTC > 50000");

        translator.translate("priceAlert", Locale.ENGLISH, "BTC", 50000);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(messageSource).getMessage(eq("priceAlert"), args.capture(), eq(Locale.ENGLISH));
        assertThat(args.getValue()).containsExactly("BTC", 50000);
    }
}
