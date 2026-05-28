package com.finance.notification.broadcast.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Source of broadcast recipients, exposing a paged stream of user subjects and a total count so the
 * broadcaster can enforce caps and dispatch in batches.
 */
public interface RecipientDirectory {

    Page<String> findUserSubs(Pageable pageable);

    long count();
}
