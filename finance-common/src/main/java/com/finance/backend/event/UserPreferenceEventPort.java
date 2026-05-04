package com.finance.backend.event;

public interface UserPreferenceEventPort {

    void publishUserPreferencesUpdated(UserPreferencesUpdatedEvent event);
}
