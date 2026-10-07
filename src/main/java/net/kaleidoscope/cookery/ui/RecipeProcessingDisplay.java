package net.kaleidoscope.cookery.ui;

import net.kaleidoscope.cookery.block.behavior.*;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.recipe.*;
import net.kaleidoscope.cookery.recipe.edit.RecipeFileStore;
import net.kaleidoscope.cookery.recipe.edit.RecipeProcessingMetadata;
import net.kaleidoscope.cookery.recipe.edit.RecipeSourceIndex;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.core.block.behavior.BlockBehavior;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

/** Menu reads are entirely in memory and run on the player's owning thread. */
final class RecipeProcessingDisplay {
    record View(String label, String value, String source, List<String> notes,
                int effectiveValue, String inheritance) {
        List<Component> lore() {
            List<Component> lore = new ArrayList<>();
            lore.add(MenuIcons.gray(label + "：" + value));
            lore.add(MenuIcons.gray("设置来源：" + source));
            if (!inheritance.isEmpty()) lore.add(MenuIcons.gray(inheritance));
            notes.forEach(line -> lore.add(MenuIcons.gray(line)));
            return lore;
        }
    }

    private RecipeProcessingDisplay() {}

    static View editing(Object recipe, ApplianceType cook, int draftValue) {
        RecipeSourceIndex index = RecipeSourceIndex.instance();
        RecipeFileStore.SourceTarget target = recipe == null ? null : index.target(recipe);
        return describe(cook, value(recipe), draftValue, true, recipe == null,
                target, applianceDefault(cook));
    }

    /** A zero default means the appliance definition is temporarily unavailable. */
    static View describe(ApplianceType cook, int originalValue, int draftValue,
                         boolean editing, boolean creating, RecipeFileStore.SourceTarget target,
                         int defaultValue) {
        RecipeProcessingMetadata.Setting setting = target == null ? null : target.processing().get(field(cook));
        boolean changed = editing && (creating ? draftValue > 0 : draftValue != originalValue);
        int effective = draftValue > 0 ? draftValue : defaultValue;
        String source;
        String inheritance = "";
        if (changed && draftValue > 0) {
            source = "配方覆盖（待保存）";
        } else if (changed) {
            source = "恢复继承（待保存）";
            if (setting == null || !setting.inheritanceKnown()) {
                effective = 0;
                inheritance = "继承目标：" + (setting == null ? "模板或厨具默认"
                        : origin(cook, setting.inheritedOrigin())) + "，保存后确认";
            } else {
                effective = setting.inheritedValue() > 0 ? setting.inheritedValue() : defaultValue;
                inheritance = "继承目标：" + origin(cook, setting.inheritedOrigin());
            }
        } else if (setting != null) {
            source = origin(cook, setting.origin());
            if (setting.origin() == RecipeProcessingMetadata.Origin.DEFAULT) effective = defaultValue;
        } else {
            source = draftValue > 0 ? "配方设置" : defaultLabel(cook);
        }
        List<String> notes = new ArrayList<>();
        if (target != null && !target.resolved()) notes.add("此配方暂不可保存");
        if (effective <= 0 && !changed) notes.add("厨具默认值暂不可用，请等待配置加载完成");
        else if ((draftValue <= 0 || setting != null && setting.origin() == RecipeProcessingMetadata.Origin.DEFAULT)
                && cook != ApplianceType.TEAPOT && cook != ApplianceType.CHOPPING_BOARD) {
            notes.add("自定义厨具使用自身默认值");
        }
        if (cook == ApplianceType.TEAPOT) notes.add("另有固定 10 秒准备阶段");
        return new View(label(cook), effective > 0 ? formatted(cook, effective) : changed ? "保存后确认" : "暂不可用",
                source, List.copyOf(notes),
                effective, inheritance);
    }

    private static String origin(ApplianceType cook, RecipeProcessingMetadata.Origin origin) {
        return switch (origin) {
            case RECIPE -> "配方设置";
            case TEMPLATE -> "模板";
            case TEMPLATE_OVERRIDE -> "模板局部覆盖";
            case FACTORY -> "生成模板";
            case FACTORY_PARAMETER -> "配方设置";
            case DEFAULT -> defaultLabel(cook);
        };
    }

    private static String defaultLabel(ApplianceType cook) {
        return cook == ApplianceType.TEAPOT || cook == ApplianceType.CHOPPING_BOARD ? "内置默认" : "厨具默认";
    }

    private static String label(ApplianceType cook) {
        return switch (cook) {
            case POT -> "翻炒次数";
            case MILLSTONE -> "研磨圈数";
            case CHOPPING_BOARD -> "刀数";
            case TEAPOT -> "熬煮时间";
            default -> "烹饪时间";
        };
    }

    private static String formatted(ApplianceType cook, int value) {
        if (value <= 0) return "保存后确认";
        return switch (cook) {
            case POT -> value + " 次";
            case MILLSTONE -> value + " 圈";
            case CHOPPING_BOARD -> value + " 刀";
            default -> RecipeProcessingControls.seconds(value) + " 秒";
        };
    }

    private static String field(ApplianceType cook) {
        return switch (cook) {
            case POT -> "stir_fry_count";
            case MILLSTONE -> "rotations";
            case CHOPPING_BOARD -> "stage";
            default -> "cooking_time";
        };
    }

    private static int value(Object recipe) {
        if (recipe instanceof AccurateFoodRecipe accurate) return accurate.cook() == ApplianceType.MILLSTONE
                ? accurate.rotations() : accurate.cookingTime();
        if (recipe instanceof FlexFoodRecipe flex) return flex.cook() == ApplianceType.POT
                ? flex.stirFryCount() : flex.cookingTime();
        if (recipe instanceof TeapotRecipe teapot) return teapot.time();
        return recipe instanceof ChoppingBoardRecipe chopping ? chopping.stage() : 0;
    }

    private static int applianceDefault(ApplianceType cook) {
        if (cook == ApplianceType.TEAPOT) return 200;
        if (cook == ApplianceType.CHOPPING_BOARD) return 1;
        try {
            return switch (cook) {
                case STEAMER -> {
                    SteamerBehavior behavior = blockBehavior(ItemKeys.MENU_STEAMER, SteamerBehavior.class);
                    yield behavior == null ? 0 : Math.max(1, behavior.cookingTime);
                }
                case SHAWARMA -> {
                    ShawarmaSpitBehavior behavior = blockBehavior(ItemKeys.MENU_SHAWARMA, ShawarmaSpitBehavior.class);
                    yield behavior == null ? 0 : Math.max(1, behavior.grillTime);
                }
                case STOCKPOT -> {
                    StockpotBehavior behavior = blockBehavior(ItemKeys.MENU_STOCKPOT, StockpotBehavior.class);
                    yield behavior == null ? 0 : Math.max(1, behavior.cookingTime);
                }
                case POT -> {
                    PotBehavior behavior = blockBehavior(ItemKeys.MENU_POT, PotBehavior.class);
                    yield behavior == null ? 0 : Math.max(1, behavior.stirFryCount);
                }
                case MILLSTONE -> {
                    var furniture = CraftEngineFurniture.byId(ItemKeys.MENU_MILLSTONE);
                    int rotations = 0;
                    if (furniture != null) for (var behavior : furniture.behaviors()) {
                        if (behavior instanceof MillstoneBehavior millstone) { rotations = Math.max(1, millstone.grindRotations); break; }
                    }
                    yield rotations;
                }
                default -> 0;
            };
        } catch (RuntimeException unavailable) {
            return 0;
        }
    }

    private static <T extends BlockBehavior> T blockBehavior(Key id, Class<T> type) {
        var block = CraftEngineBlocks.byId(id);
        return block == null ? null : block.defaultState().behavior().getFirst(type);
    }
}
