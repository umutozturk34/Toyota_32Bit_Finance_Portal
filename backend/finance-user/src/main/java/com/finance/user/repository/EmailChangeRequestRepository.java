package com.finance.user.repository;

import com.finance.user.model.EmailChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailChangeRequestRepository extends JpaRepository<EmailChangeRequest, String> {
}
