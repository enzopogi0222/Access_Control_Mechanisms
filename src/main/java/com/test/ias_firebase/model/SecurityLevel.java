package com.test.ias_firebase.model;

import java.util.Locale;

public enum SecurityLevel {
    PUBLIC(0),
    INTERNAL(1),
    CONFIDENTIAL(2),
    SECRET(3);

    private final int rank;

    SecurityLevel(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean atLeast(SecurityLevel other) {
        if (other == null) return true;
        return this.rank >= other.rank;
    }

    public static SecurityLevel parseOrDefault(String value, SecurityLevel defaultLevel) {
        if (value == null) return defaultLevel;
        String v = value.trim();
        if (v.isBlank()) return defaultLevel;
        try {
            return SecurityLevel.valueOf(v.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return defaultLevel;
        }
    }
}

