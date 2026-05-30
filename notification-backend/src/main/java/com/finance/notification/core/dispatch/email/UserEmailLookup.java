package com.finance.notification.core.dispatch.email;

import java.util.Optional;

/**
 * Resolves a user's email address from their subject identifier for outbound mail. Returns empty
 * when no address is known, so callers can silently skip the email channel.
 */
public interface UserEmailLookup {

    Optional<String> findEmail(String userSub);
}
