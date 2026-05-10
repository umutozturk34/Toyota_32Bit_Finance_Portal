package com.finance.notification.news.dispatch;

import com.finance.common.i18n.Translator;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.NewsPublishedPayload;
import com.finance.notification.core.dispatch.slot.SlotProperties;
import com.finance.notification.core.dispatch.slot.SlotResolver;
import com.finance.notification.testsupport.HandlerTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NewsPublishedHandlerTest {

    private static SlotResolver newResolver() {
        Map<String, List<String>> keywords = new LinkedHashMap<>();
        keywords.put("morning", List.of("morning", "sabah"));
        keywords.put("noon", List.of("afternoon", "midday", "noon"));
        keywords.put("evening", List.of("evening", "aksam", "akşam"));
        return new SlotResolver(new SlotProperties(keywords));
    }

    private NewsPublishedHandler handler;

    @BeforeEach
    void setUp() {
        Translator translator = HandlerTestSupport.turkishTranslator();
        handler = new NewsPublishedHandler(newResolver(), translator);
    }

    @AfterEach
    void tearDown() {
        HandlerTestSupport.resetLocale();
    }

    private NotificationRequest requestWithSource(int count, String source) {
        return NotificationRequest.of("user-1",
                new NewsPublishedPayload(count, List.of(), List.of(), source));
    }

    @Test
    void should_renderSlotTitle_when_sourceContainsMorningOrAfternoonOrEvening() {
        RenderedNotification morning = handler.render(requestWithSource(5, "scheduled-news-morning"));
        RenderedNotification afternoon = handler.render(requestWithSource(3, "scheduled-news-afternoon"));
        RenderedNotification evening = handler.render(requestWithSource(7, "scheduled-news-evening"));

        assertThat(morning.title()).isEqualTo("Sabah haberleri · 5 yeni başlık");
        assertThat(afternoon.title()).isEqualTo("Öğlen haberleri · 3 yeni başlık");
        assertThat(evening.title()).isEqualTo("Akşam haberleri · 7 yeni başlık");
    }

    @Test
    void should_renderGenericTitle_when_sourceUnknownOrNull() {
        RenderedNotification adminSource = handler.render(requestWithSource(4, "admin"));
        RenderedNotification nullSource = handler.render(requestWithSource(0, null));

        assertThat(adminSource.title()).isEqualTo("4 yeni haber yayımlandı");
        assertThat(nullSource.title()).isEqualTo("Yeni haberler yayımlandı");
    }

    @Test
    void should_omitCountFromSlotTitle_when_articleCountZero() {
        RenderedNotification rendered = handler.render(requestWithSource(0, "scheduled-news-morning"));

        assertThat(rendered.title()).isEqualTo("Sabah haberleri");
    }
}
