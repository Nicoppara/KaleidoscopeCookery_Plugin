package net.kaleidoscope.cookery.ui;

import net.kaleidoscope.cookery.api.ui.MenuButton;
import net.kaleidoscope.cookery.recipe.FoodGroups;
import net.kaleidoscope.cookery.recipe.edit.FoodGroupDraft;
import net.kaleidoscope.cookery.recipe.edit.RecipeEditService;
import net.kaleidoscope.cookery.ui.input.MenuInput;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.gui.BasicGuiImpl;
import net.momirealms.craftengine.core.plugin.gui.Gui;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.Ingredient;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// 单个食材分组的编辑页 标签 id 用途 成员三样一起存
public final class FoodGroupEditMenu {
    private static final int MAX_MEMBERS = 14;

    private FoodGroupEditMenu() {
    }

    public static void open(org.bukkit.entity.Player bukkitPlayer, FoodGroupDraft draft) {
        Player viewer = RecipeMenus.adapt(bukkitPlayer);
        if (viewer == null) {
            return;
        }
        GuiLayout layout = new GuiLayout(
                "#########",
                "###T#K###",
                "#MMMMMMM#",
                "#MMMMMMM#",
                "#########",
                "B###S###D");
        layout.addIngredient('#', Ingredient.simple(MenuIcons.filler(viewer)));
        layout.addIngredient('T', tagSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('K', kindSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('M', memberSlots(bukkitPlayer, viewer, draft));
        layout.addIngredient('B', MenuIcons.back(viewer, () -> FoodGroupMenu.open(bukkitPlayer)));
        layout.addIngredient('S', saveSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('D', deleteSlot(bukkitPlayer, viewer, draft));

        Gui gui = BasicGuiImpl.builder()
                .layout(layout)
                .inventoryClickConsumer(RecipeMenus.inventoryGuard())
                .build();
        gui.title(MenuIcons.text(draft.isNew() ? "新建食材分组" : "编辑食材分组", NamedTextColor.DARK_GRAY))
                .refresh()
                .open(viewer);
    }

    private static GuiElement tagSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                      FoodGroupDraft draft) {
        Item icon = MenuIcons.icon(MenuButton.CREATE, viewer,
                MenuIcons.text("标签 id", NamedTextColor.GOLD),
                MenuIcons.lore(draft.tag().asString(), "左键修改"));
        return MenuIcons.button(icon, () -> MenuInput.requestText(bukkitPlayer, "标签 id", "id",
                draft.tag().asString(),
                raw -> {
                    Key key = RecipeMenus.parseKey(raw);
                    if (key == null) {
                        RecipeMenus.message(bukkitPlayer, "标签 id 格式不正确");
                    } else {
                        draft.tag(key);
                    }
                    open(bukkitPlayer, draft);
                },
                () -> open(bukkitPlayer, draft)));
    }

    private static GuiElement kindSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                       FoodGroupDraft draft) {
        FoodGroups.Kind kind = draft.kind();
        boolean seasoning = kind == FoodGroups.Kind.SEASONING;
        List<Component> lore = new ArrayList<>();
        lore.add(MenuIcons.text(seasoning
                ? "进锅只占一格 不进配比 不算杂料"
                : "同组食材互相顶替 配方写哪个都行", NamedTextColor.GRAY));
        lore.add(MenuIcons.text("左键切换成 "
                + (seasoning ? FoodGroups.Kind.EQUIVALENT : FoodGroups.Kind.SEASONING).displayName(),
                NamedTextColor.YELLOW));
        Item icon = MenuIcons.icon(MenuButton.MODE, viewer,
                MenuIcons.text("用途 " + kind.displayName(),
                        seasoning ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.AQUA), lore);
        return MenuIcons.button(icon, () -> {
            draft.kind(seasoning ? FoodGroups.Kind.EQUIVALENT : FoodGroups.Kind.SEASONING);
            open(bukkitPlayer, draft);
        });
    }

    private static Ingredient memberSlots(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                          FoodGroupDraft draft) {
        return new Ingredient() {
            private int index = 0;

            @Override
            public GuiElement element(Gui gui) {
                return memberSlot(bukkitPlayer, viewer, draft, this.index++);
            }
        };
    }

    private static GuiElement memberSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                         FoodGroupDraft draft, int index) {
        List<Key> members = draft.memberList();
        if (index < members.size()) {
            return existingSlot(bukkitPlayer, viewer, draft, members.get(index), index);
        }
        if (index > members.size() || members.size() >= MAX_MEMBERS) {
            return MenuIcons.empty();
        }
        Item icon = MenuIcons.icon(MenuButton.ADD, viewer,
                MenuIcons.text("添加物品", NamedTextColor.GREEN),
                MenuIcons.lore("光标持物品左键 直接取该物品", "空手左键 手动输入物品 id"));
        return GuiElement.constant(icon, (element, click) -> {
            click.cancel();
            AccurateEditMenu.pickItem(bukkitPlayer, click, "添加物品", null,
                    draft.members()::add,
                    () -> open(bukkitPlayer, draft));
        });
    }

    private static GuiElement existingSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                           FoodGroupDraft draft, Key member, int index) {
        List<Component> lore = MenuIcons.loreNamed(member);
        lore.add(MenuIcons.text("左键换物品", NamedTextColor.YELLOW));
        lore.add(MenuIcons.text("Shift 右键移出分组", NamedTextColor.RED));
        Item icon = MenuIcons.icon(member, viewer,
                MenuIcons.text("成员 " + (index + 1), NamedTextColor.GOLD), lore);
        return GuiElement.constant(icon, (element, click) -> {
            click.cancel();
            if ("SHIFT_RIGHT".equals(click.type())) {
                draft.members().remove(member);
                open(bukkitPlayer, draft);
                return;
            }
            AccurateEditMenu.pickItem(bukkitPlayer, click, "更换物品", member,
                    key -> replaceMember(draft, member, key),
                    () -> open(bukkitPlayer, draft));
        });
    }

    // 原地替换 免得改一个物品整组次序全变
    private static void replaceMember(FoodGroupDraft draft, Key oldMember, Key newMember) {
        List<Key> members = draft.memberList();
        members.set(members.indexOf(oldMember), newMember);
        draft.members().clear();
        draft.members().addAll(members);
    }

    private static GuiElement saveSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                       FoodGroupDraft draft) {
        Item icon = MenuIcons.icon(MenuButton.SAVE, viewer,
                MenuIcons.text("保存", NamedTextColor.GREEN),
                MenuIcons.lore("立即生效 并写回配置文件"));
        return MenuIcons.button(icon, () -> {
            String error = RecipeEditService.validateFoodGroup(draft.tag(), draft.memberList());
            if (error != null) {
                RecipeMenus.message(bukkitPlayer, error);
                open(bukkitPlayer, draft);
                return;
            }
            RecipeMenus.message(bukkitPlayer, "正在保存分组...");
            // 改过 id 的话旧标签要一并摘掉 否则配置里会留下一份孤儿分组
            Key stale = !draft.isNew() && !draft.originalTag().equals(draft.tag())
                    ? draft.originalTag() : null;
            RecipeEditService.saveFoodGroup(draft.tag(), draft.memberList(), draft.kind())
                    .thenCompose(saveError -> saveError != null || stale == null
                            ? CompletableFuture.completedFuture(saveError)
                            : RecipeEditService.deleteFoodGroup(stale).thenApply(ok -> null))
                    .thenAccept(saveError -> MenuTasks.runFor(bukkitPlayer, () -> {
                        if (saveError != null) {
                            RecipeMenus.message(bukkitPlayer, saveError);
                            open(bukkitPlayer, draft);
                            return;
                        }
                        RecipeMenus.message(bukkitPlayer, "已保存分组 " + draft.tag().asString());
                        FoodGroupMenu.open(bukkitPlayer);
                    }));
        });
    }

    private static GuiElement deleteSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                         FoodGroupDraft draft) {
        if (draft.isNew()) {
            return MenuIcons.empty();
        }
        Item icon = MenuIcons.icon(MenuButton.DELETE, viewer,
                MenuIcons.text("删除分组", NamedTextColor.RED),
                MenuIcons.lore("从配置里移除该标签与它的用途"));
        return MenuIcons.button(icon, () -> ConfirmMenu.open(bukkitPlayer, "删除食材分组",
                List.of(draft.originalTag().asString()),
                () -> RecipeEditService.deleteFoodGroup(draft.originalTag()).thenAccept(ok ->
                        MenuTasks.runFor(bukkitPlayer, () -> {
                            RecipeMenus.message(bukkitPlayer, ok
                                    ? "已删除分组 " + draft.originalTag().asString()
                                    : "删除失败 分组未改动");
                            FoodGroupMenu.open(bukkitPlayer);
                        })),
                () -> open(bukkitPlayer, draft)));
    }
}
