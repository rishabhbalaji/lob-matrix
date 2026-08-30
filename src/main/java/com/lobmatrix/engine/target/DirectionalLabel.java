package com.lobmatrix.engine.target;

/**
 * Tri-class directional target label {-1, 0, +1} for supervised machine learning models.
 */
public enum DirectionalLabel {
    DOWN(-1),
    NEUTRAL(0),
    UP(+1);

    private final int value;

    DirectionalLabel(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static DirectionalLabel fromValue(int value) {
        if (value > 0) return UP;
        if (value < 0) return DOWN;
        return NEUTRAL;
    }
}
