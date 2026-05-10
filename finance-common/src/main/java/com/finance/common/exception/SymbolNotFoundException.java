package com.finance.common.exception;

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
