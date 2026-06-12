package com.finance.user.repository;

import com.finance.user.model.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link UserPreference} rows, keyed by the user's Keycloak subject. */
public interface UserPreferenceRepository extends JpaRepository<UserPreference, String> {
}
