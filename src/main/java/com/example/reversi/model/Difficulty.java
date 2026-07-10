package com.example.reversi.model;

public enum Difficulty {
    EASY(1, "Easy"),
    NORMAL(2, "Normal"),
    HARD(4, "Hard"),
    ULTIMATE(6, "Ultimate");

    private final int depth;
    private final String label;

    Difficulty(int depth, String label) {
        this.depth = depth;
        this.label = label;
    }

    public int getDepth() {
        return depth;
    }

    public String getLabel() {
        return label;
    }

    public static Difficulty from(String value) {
        if (value == null)
            return NORMAL;
        for (Difficulty d : values()) {
            if (d.name().equalsIgnoreCase(value) || d.label.equalsIgnoreCase(value))
                return d;
        }
        return NORMAL;
    }
}
