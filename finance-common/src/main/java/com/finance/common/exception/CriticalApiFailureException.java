package com.finance.common.exception;

/**
 * A {@link BusinessException} signalling that a batch external-API update crossed the failure-rate
 * threshold — a likely systemic outage rather than isolated noise. Carries the {@code CRITICAL_API_FAILURE}
 * error code so callers (e.g. the admin task runner) can tell a partial-data refresh apart from a real bug.
 */
public class CriticalApiFailureException extends BusinessException {

    public CriticalApiFailureException(String message) {
        super(message);
    }

    @Override
    public String getErrorCode() {
        return "CRITICAL_API_FAILURE";
    }
}
