package com.finance.portfolio.derivative.model;

/** Why a derivative position was closed: explicit user action vs. automatic close at contract expiry. */
public enum DerivativeCloseReason {
    USER_CLOSED,
    EXPIRED
}
