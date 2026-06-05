package com.finance.user.repository;

import com.finance.user.model.UserRecentSearch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Persistence for {@link UserRecentSearch} recent-search lists, keyed by the user's Keycloak subject. */
@Repository
public interface UserRecentSearchRepository extends JpaRepository<UserRecentSearch, String> {
}
