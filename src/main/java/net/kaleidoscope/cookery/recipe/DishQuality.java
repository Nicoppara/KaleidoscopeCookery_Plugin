package net.kaleidoscope.cookery.recipe;

// 菜品品质 由实际投料与配方理想配比的贴合度决定 倍率作用于饱食度饱和度与效果时长
// 物品自己声明的食物属性是上限 正常发挥只拿 STANDARD 的 0.6
public enum DishQuality {
    SUPERB(0.95, 1.2, "gold"),
    EXCELLENT(0.82, 0.9, "green"),
    STANDARD(0.55, 0.6, "white"),
    POOR(0.0, 0.3, "dark_gray");

    // 达到该档所需的最低得分
    private final double minScore;
    // 食物属性倍率
    private final double ratio;
    private final String color;

    DishQuality(double minScore, double ratio, String color) {
        this.minScore = minScore;
        this.ratio = ratio;
        this.color = color;
    }

    public double ratio() {
        return ratio;
    }

    public String color() {
        return color;
    }

    public String translationKey() {
        return "lore.kaleidoscopecookery.quality." + name().toLowerCase();
    }

    // 档位按 minScore 降序声明 第一个够得着的就是结果
    public static DishQuality of(double score) {
        for (DishQuality quality : values()) {
            if (score >= quality.minScore) {
                return quality;
            }
        }
        return POOR;
    }
}
