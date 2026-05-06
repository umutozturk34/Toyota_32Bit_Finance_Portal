package com.finance.common.event;

public interface UserPreferenceEventPort {

    void publishUserPreferencesUpdated(UserPreferencesUpdatedEvent event);
}
