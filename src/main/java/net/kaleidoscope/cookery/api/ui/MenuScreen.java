package net.kaleidoscope.cookery.api.ui;

/**
 * Recipe menu screens whose title can be replaced.
 * A template may use the placeholders {@code <appliance>} (appliance display
 * name), {@code <count>} (entries on the screen) and {@code <recipe>} (display
 * name of the recipe result).
 * Templates are parsed as MiniMessage after substitution, so colour tags work;
 * a template without one keeps the built-in dark gray.
 */
public enum MenuScreen {
    HOME_BROWSE("食谱一览 - 选择厨具"),
    HOME_EDIT("食谱编辑 - 选择厨具"),
    LIST_BROWSE("一览 - <appliance> (<count>)"),
    LIST_EDIT("编辑 - <appliance> (<count>)"),
    CREATE_PICK_TYPE("新建食谱 - <appliance>"),
    DETAIL_ACCURATE("精准食谱 - <recipe>"),
    DETAIL_FLEX("模糊食谱 - <recipe>"),
    DETAIL_CHOPPING("砧板食谱 - <recipe>"),
    DETAIL_TEAPOT("茶壶食谱 - <recipe>"),
    SOUP_BASE("汤底表 - 共 <count> 种"),
    FOOD_GROUP("食材分组 - 共 <count> 组");

    private final String defaultTitle;

    MenuScreen(String defaultTitle) {
        this.defaultTitle = defaultTitle;
    }

    public String defaultTitle() {
        return defaultTitle;
    }
}
