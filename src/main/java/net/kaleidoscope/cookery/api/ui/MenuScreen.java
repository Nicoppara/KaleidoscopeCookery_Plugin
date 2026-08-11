package net.kaleidoscope.cookery.api.ui;

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
   SOUP_BASE("汤底表 - 共 <count> 种");

   private final String defaultTitle;

   MenuScreen(String defaultTitle) {
      this.defaultTitle = defaultTitle;
   }

   public String defaultTitle() {
      return this.defaultTitle;
   }
}
