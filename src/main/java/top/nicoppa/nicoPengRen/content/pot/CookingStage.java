package top.nicoppa.nicoPengRen.content.pot;

public enum CookingStage {
    IDLE,
    COOKING,
    DONE,
    BURNT;

    private static final CookingStage[] VALUES = values();

    public static CookingStage fromOrdinal(int ordinal) {
        return (ordinal >= 0 && ordinal < VALUES.length) ? VALUES[ordinal] : IDLE;
    }
}