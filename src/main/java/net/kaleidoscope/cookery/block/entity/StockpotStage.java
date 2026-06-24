package net.kaleidoscope.cookery.block.entity;

// 高汤锅烹饪阶段
public enum StockpotStage {
    PUT_SOUP_BASE,
    PUT_INGREDIENT,
    COOKING,
    FINISHED;

    public static StockpotStage fromOrdinal(int ordinal) {
        StockpotStage[] values = values();
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        return PUT_SOUP_BASE;
    }
}
