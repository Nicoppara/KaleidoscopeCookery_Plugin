package net.kaleidoscope.cookery.recipe;

// 厨具类型 配方按厨具区分归属
public enum ApplianceType {
    POT,
    STEAMER,
    STOCKPOT,
    SHAWARMA,
    MILLSTONE,
    CHOPPING_BOARD,
    TEAPOT;

    // 走模糊配方的厨具 等效食物表与调味品表只对这两个有意义 其余是一进一出的精确配方
    public boolean usesFlexRecipes() {
        return this == POT || this == STOCKPOT;
    }
}
