package com.finance.user.repository;

import com.finance.user.model.EmailChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for pending {@link EmailChangeRequest} rows, keyed by the user's Keycloak subject. */
public interface EmailChangeRequestRepository extends JpaRepository<EmailChangeRequest, String> {
}
