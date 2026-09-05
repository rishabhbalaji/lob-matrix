package com.lobmatrix.source;

import java.util.Locale;

public enum MarketDataSource {
    MOCK,
    ZERODHA,
    DHAN,
    UPSTOX;

    public static MarketDataSource from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Source must be provided.");
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported source: " + value);
        }
    }

    public boolean isImplemented() {
        return this == MOCK;
    }
}
