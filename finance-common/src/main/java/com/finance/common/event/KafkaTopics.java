package com.finance.common.event;

public final class KafkaTopics {

    public static final String MARKET_UPDATED = "market.updated";
    public static final String USER_PREFERENCES_UPDATED = "user.preferences.updated";
    public static final String USER_EMAIL_CHANGE_CODE = "user.email-change.code-requested";
    public static final String NEWS_PUBLISHED = "news.published";
    public static final String PORTFOLIO_UPDATED = "portfolio.updated";

    private KafkaTopics() {
    }
}
