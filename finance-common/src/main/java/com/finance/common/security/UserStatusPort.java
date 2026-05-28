package com.finance.common.security;

import java.util.Collection;
import java.util.Map;

/**
 * Outbound port for querying whether a user account is active, decoupling callers from the storage
 * mechanism. Implementations treat an unknown subject as active (fail-open) so missing local mirror
 * rows do not lock out otherwise-valid users.
 */
public interface UserStatusPort {

    boolean isActive(String userSub);

    /**
     * Bulk active-status lookup; every requested non-blank subject appears in the result, defaulting
     * to active when no record exists.
     */
    Map<String, Boolean> activeStatusOf(Collection<String> userSubs);
}
