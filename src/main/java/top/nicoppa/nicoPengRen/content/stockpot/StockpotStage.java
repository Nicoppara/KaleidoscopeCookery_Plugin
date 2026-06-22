package top.nicoppa.nicoPengRen.content.stockpot;

/**
 * 高汤锅烹饪阶段
 */
public enum StockpotStage {
    PUT_SOUP_BASE,
    PUT_INGREDIENT,
    COOKING,
    FINISHED;

    public static StockpotStage fromOrdinal(int ordinal) {
        StockpotStage[] values = values();
        return (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : PUT_SOUP_BASE;
    }
}
