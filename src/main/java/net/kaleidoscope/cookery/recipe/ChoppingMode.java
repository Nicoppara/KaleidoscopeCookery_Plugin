package net.kaleidoscope.cookery.recipe;

// 砧板产出模式 权重在 SINGLE 里是相对权重 在 EXTRA 与 MULTI_RANDOM 里当百分比独立判定
// MULTI_RANDOM 全部未命中时按权重随机保底产出一个
public enum ChoppingMode {
    SINGLE,
    SINGLE_EXTRA,
    MULTI_RANDOM;

    // 空值与无法识别的配置值一律回退 SINGLE 不报错
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
