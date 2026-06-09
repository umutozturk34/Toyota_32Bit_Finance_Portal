package com.finance.common.exception;

/**
 * Thrown when an operation needs market data (prices/FX) that the cold-start load has not finished
 * providing yet. Maps to HTTP 503 so the client knows to retry once the data is ready, rather than the
 * request half-completing against missing data.
 */
public class MarketDataNotReadyException extends RuntimeException {

    public MarketDataNotReadyException(String message) {
        super(message);
    }
}
