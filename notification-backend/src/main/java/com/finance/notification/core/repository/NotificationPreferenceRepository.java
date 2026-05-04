package com.finance.notification.core.repository;

import com.finance.notification.core.model.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> {
}
