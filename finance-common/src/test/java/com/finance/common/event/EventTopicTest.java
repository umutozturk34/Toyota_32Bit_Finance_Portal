package com.finance.common.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class EventTopicTest {

    private static final KafkaTopicsProperties TOPICS = new KafkaTopicsProperties(
            "market.updated",
            "news.published",
            "portfolio.updated",
            "macro.indicators.updated",
            "user.email-change-code",
            "user.registered",
            "mail.dispatch");

    @ParameterizedTest
    @CsvSource({
            "MARKET_UPDATED, market.updated",
            "NEWS_PUBLISHED, news.published",
            "PORTFOLIO_UPDATED, portfolio.updated",
            "MACRO_INDICATORS_UPDATED, macro.indicators.updated",
            "USER_EMAIL_CHANGE_CODE, user.email-change-code",
            "USER_REGISTERED, user.registered"
    })
    void resolve_returnsConfiguredTopicName_forEachConstant(EventTopic topic, String expected) {
        assertThat(topic.resolve(TOPICS)).isEqualTo(expected);
    }

    @Test
    void values_exposeEverySupportedTopic() {
        assertThat(EventTopic.values()).containsExactly(
                EventTopic.MARKET_UPDATED,
                EventTopic.NEWS_PUBLISHED,
                EventTopic.PORTFOLIO_UPDATED,
                EventTopic.MACRO_INDICATORS_UPDATED,
                EventTopic.USER_EMAIL_CHANGE_CODE,
                EventTopic.USER_REGISTERED);
    }
}
