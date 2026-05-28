package com.finance.common.exception;

/**
 * Raised when a market symbol cannot be resolved; the unknown {@code symbol} is passed as the i18n
 * message argument and the exception is mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class SymbolNotFoundException extends RuntimeException {

    private final String symbol;

    public SymbolNotFoundException(String symbol) {
        super("error.symbol.notFound");
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    public Object[] getMessageArgs() {
        return new Object[] { symbol };
    }
}
