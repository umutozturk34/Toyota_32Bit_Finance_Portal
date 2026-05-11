package com.finance.common.security;

import java.util.Collection;

public interface UserStatusPort {

    boolean isActive(String userSub);

    default void preload(Collection<String> userSubs) {
    }
}
