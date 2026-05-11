package com.finance.common.security;

import java.util.Collection;
import java.util.Map;

public interface UserStatusPort {

    boolean isActive(String userSub);

    Map<String, Boolean> activeStatusOf(Collection<String> userSubs);
}
