package net.kaleidoscope.cookery.block.entity;

public enum PotStage {
    IDLE,
    COOKING,
    DONE,
    BURNT;

    private static final PotStage[] VALUES = values();

    public static PotStage fromOrdinal(int ordinal) {
        return (ordinal >= 0 && ordinal < VALUES.length) ? VALUES[ordinal] : IDLE;
    }
}