package com.finance.user.repository;

import com.finance.user.model.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Persistence for {@link UserPreference} rows, keyed by the user's Keycloak subject. */
@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, String> {
}
