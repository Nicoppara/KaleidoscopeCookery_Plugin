package net.kaleidoscope.cookery.recipe;

// 砧板产出模式
// SINGLE        单产物 按权重随机选一个产出
// SINGLE_EXTRA  单产物 + 附带产物 主产物按权重选一个 附带产物各自按权重当作百分比独立判定是否产出
// MULTI_RANDOM  多产物随机 每个产物按权重当作百分比独立判定 全部未命中时再按权重随机保底产出一个
/**
 * Result rolling mode for chopping board recipes.
 */
public enum ChoppingMode {
    SINGLE,
    SINGLE_EXTRA,
    MULTI_RANDOM;

    /**
     * Parses a config value into a chopping mode.
     *
     * @param raw config value
     * @return parsed mode, or {@link #SINGLE} for blank or unknown values
     */
    public static ChoppingMode fromConfig(String raw) {
        if (raw == null) {
            return SINGLE;
        }
        return switch (raw.trim().toLowerCase()) {
            case "single_extra", "single-extra" -> SINGLE_EXTRA;
            case "multi_random", "multi-random" -> MULTI_RANDOM;
            default -> SINGLE;
        };
    }
}
