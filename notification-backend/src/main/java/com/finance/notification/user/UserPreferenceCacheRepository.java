package com.finance.notification.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPreferenceCacheRepository extends JpaRepository<UserPreferenceCache, String> {

    List<UserPreferenceCache> findByReportFrequency(String reportFrequency);
}
