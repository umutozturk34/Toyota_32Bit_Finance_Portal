package com.finance.common.event;

import java.util.function.Function;

public enum EventTopic {
    MARKET_UPDATED(KafkaTopicsProperties::marketUpdated),
    NEWS_PUBLISHED(KafkaTopicsProperties::newsPublished),
    PORTFOLIO_UPDATED(KafkaTopicsProperties::portfolioUpdated),
    MACRO_INDICATORS_UPDATED(KafkaTopicsProperties::macroIndicatorsUpdated),
    USER_EMAIL_CHANGE_CODE(KafkaTopicsProperties::userEmailChangeCode),
    USER_REGISTERED(KafkaTopicsProperties::userRegistered);

    private final Function<KafkaTopicsProperties, String> accessor;

    EventTopic(Function<KafkaTopicsProperties, String> accessor) {
        this.accessor = accessor;
    }

    public String resolve(KafkaTopicsProperties props) {
        return accessor.apply(props);
    }
}
