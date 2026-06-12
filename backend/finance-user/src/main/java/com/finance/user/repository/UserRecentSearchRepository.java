package com.finance.user.repository;

import com.finance.user.model.UserRecentSearch;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link UserRecentSearch} recent-search lists, keyed by the user's Keycloak subject. */
public interface UserRecentSearchRepository extends JpaRepository<UserRecentSearch, String> {
}
