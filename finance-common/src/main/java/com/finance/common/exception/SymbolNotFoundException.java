package com.finance.common.exception;

public class SymbolNotFoundException extends RuntimeException {

    private final String symbol;

    public SymbolNotFoundException(String symbol) {
        super("Symbol not found on external provider: " + symbol);
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
