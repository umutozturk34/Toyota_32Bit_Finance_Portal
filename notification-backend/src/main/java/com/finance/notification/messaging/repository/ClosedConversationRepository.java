package com.finance.notification.messaging.repository;

import com.finance.notification.messaging.model.ClosedConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClosedConversationRepository extends JpaRepository<ClosedConversation, String> {
}
