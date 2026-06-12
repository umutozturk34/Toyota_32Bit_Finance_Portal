package com.finance.user.repository;

import com.finance.user.model.UserLayout;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link UserLayout} dashboard layouts, keyed by the user's Keycloak subject. */
public interface UserLayoutRepository extends JpaRepository<UserLayout, String> {
}
