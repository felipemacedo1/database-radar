package io.databaseradar.graph;

public enum Confidence {
    UNKNOWN(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    private final int rank;

    Confidence(int rank) {
        this.rank = rank;
    }

    public static Confidence min(Confidence left, Confidence right) {
        return left.rank <= right.rank ? left : right;
    }

    public static Confidence max(Confidence left, Confidence right) {
        return left.rank >= right.rank ? left : right;
    }
}
