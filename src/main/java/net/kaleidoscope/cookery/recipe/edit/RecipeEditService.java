package net.kaleidoscope.cookery.recipe.edit;

import net.kaleidoscope.cookery.recipe.AccurateFoodRecipe;
import net.kaleidoscope.cookery.recipe.ApplianceFoodRegistry;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.ChoppingBoardRecipe;
import net.kaleidoscope.cookery.recipe.ChoppingMode;
import net.kaleidoscope.cookery.recipe.ChoppingResult;
import net.kaleidoscope.cookery.recipe.FlexFoodRecipe;
import net.kaleidoscope.cookery.recipe.TeapotRecipe;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.SoupBaseRegistry;
import net.kaleidoscope.cookery.recipe.WeightedResult;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.util.Key;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

// UI 编辑配方的落地点 保存与删除均先落盘再更新运行时
public final class RecipeEditService {
    private static final String SAVE_FAILED = "配置文件写入失败，原食谱未修改";
    private static final String HOT_UPDATE_FAILED = "配置文件已写入，运行时更新失败，请重载配置";

    private RecipeEditService() {
    }

    @FunctionalInterface
    private interface SaveAction {
        void run(Runnable afterSave) throws Exception;
    }

    // 校验失败返回错误文案 通过返回 null 调用方据此提示玩家
    public static String validate(AccurateRecipeDraft draft) {
        String idError = validateId(draft.id());
        if (idError != null) {
            return idError;
        }
        if (draft.input() == null) {
            return "请先设置原料";
        }
        if (draft.results().isEmpty()) {
            return "请先设置至少一个成品";
        }
        if (isTakenId(draft.id(), draft.originalId(),
                k -> RecipeSourceIndex.instance().hasSource(RecipeSourceIndex.Kind.ACCURATE, k))) {
            return "该 id 已被占用";
        }
        return null;
    }

    public static String validate(FlexRecipeDraft draft) {
        String idError = validateId(draft.id());
        if (idError != null) {
            return idError;
        }
        if (draft.result() == null) {
            return "请先设置成品";
        }
        if (draft.perfect().isEmpty()) {
            return "请先设置至少一种原料";
        }
        if (isTakenId(draft.id(), draft.originalId(),
                k -> RecipeSourceIndex.instance().hasSource(
                        draft.cook() == ApplianceType.STOCKPOT
                                ? RecipeSourceIndex.Kind.STOCK_FLEX : RecipeSourceIndex.Kind.POT_FLEX, k))) {
            return "该 id 已被占用";
        }
        return null;
    }

    // 改 id 时才查重 沿用原 id 的情况跳过 否则编辑时保存自己就会撞上自己
    // exists 必须查对应那一类的注册表 各类配方的 id 互不冲突
    private static boolean isTakenId(Key id, Key originalId, Predicate<Key> exists) {
        if (originalId != null && originalId.equals(id)) {
            return false;
        }
        return exists.test(id);
    }

    // YamlConfiguration 的路径分隔符是点 id 带点会被当成多级路径 写坏配置
    private static String validateId(Key id) {
        if (id == null) {
            return "配方 id 无效";
        }
        if (id.value().indexOf('.') >= 0 || id.namespace().indexOf('.') >= 0) {
            return "配方 id 不能包含点号";
        }
        return null;
    }

    public static CompletableFuture<String> saveAccurate(AccurateRecipeDraft draft) {
        String error = validate(draft);
        if (error != null) {
            return CompletableFuture.completedFuture(error);
        }
        AccurateFoodRecipe recipe = draft.toRecipe();
        Key oldId = draft.originalId();
        Path file = resolveFile(draft.originalRecipe(), RecipeFileStore::defaultAccurateFile);
        if (file == null) {
            return CompletableFuture.completedFuture("找不到可写入的配方文件");
        }
        String oldNode = RecipeSourceIndex.instance().nodePath(draft.originalRecipe());
        RecipeFileStore.SourceTarget oldTarget = RecipeSourceIndex.instance().target(draft.originalRecipe());
        String nodePath = resolveNodePath(oldNode, RecipeFileStore.accurateSections(), recipe.id());
        AccurateFoodRecipe old = draft.originalRecipe();
        RecipeSourceIndex.Kind kind = RecipeSourceIndex.Kind.ACCURATE;
        Map<String, Object> node = accurateNode(recipe);
        return persistThenApply(recipe.id(), kind,
                afterSave -> RecipeFileStore.replaceTarget(file, oldTarget, nodePath, node, afterSave),
                () -> {
                    Object replaced = RecipeSourceIndex.instance()
                            .removeSource(kind, recipe.id(), file, nodePath);
                    removeMenuRecipe(replaced);
                    if (replaced != null) {
                        removeRuntime(kind, recipe.id());
                    }
                    if (old != null) {
                        FoodRecipeRegistry.instance().removeAccurate(oldId);
                        FoodRecipeRegistry.instance().removeMenuAccurate(old);
                        RecipeSourceIndex.instance().remove(old);
                    }
                    RecipeSourceIndex.instance().restore(kind, recipe.id(), file, nodePath);
                    boolean duplicate = RecipeSourceIndex.instance()
                            .hasOtherSource(kind, recipe.id(), file, nodePath);
                    FoodRecipeRegistry.instance().registerMenuAccurate(recipe);
                    RecipeSourceIndex.instance().put(kind, recipe.id(), file, nodePath, recipe, duplicate);
                    if (!duplicate) {
                        FoodRecipeRegistry.instance().registerAccurate(recipe);
                        ApplianceFoodRegistry.instance().register(recipe.cook(), recipe.input());
                    }
                    if (old != null && !old.input().equals(recipe.input())) {
                        dropInputIfUnused(old.cook(), old.input(), k -> accurateUses(old.cook(), k));
                    }
                });
    }

    public static CompletableFuture<String> saveFlex(FlexRecipeDraft draft) {
        String error = validate(draft);
        if (error != null) {
            return CompletableFuture.completedFuture(error);
        }
        FlexFoodRecipe recipe = draft.toRecipe();
        Key oldId = draft.originalId();
        String[] sections = RecipeFileStore.flexSections(recipe.cook());
        Path file = resolveFile(draft.originalRecipe(), () -> RecipeFileStore.defaultFlexFile(recipe.cook()));
        if (file == null) {
            return CompletableFuture.completedFuture("找不到可写入的配方文件");
        }
        String oldNode = RecipeSourceIndex.instance().nodePath(draft.originalRecipe());
        RecipeFileStore.SourceTarget oldTarget = RecipeSourceIndex.instance().target(draft.originalRecipe());
        String nodePath = resolveNodePath(oldNode, sections, recipe.id());
        FlexFoodRecipe old = draft.originalRecipe();
        RecipeSourceIndex.Kind kind = recipe.cook() == ApplianceType.STOCKPOT
                ? RecipeSourceIndex.Kind.STOCK_FLEX : RecipeSourceIndex.Kind.POT_FLEX;
        boolean duplicate = RecipeSourceIndex.instance().recipes(kind, recipe.id()).stream()
                .anyMatch(existing -> existing != old);
        FlexFoodRecipe clash = duplicate ? null
                : FoodRecipeRegistry.instance().findSameDirection(recipe, old);
        if (clash != null) {
            return CompletableFuture.completedFuture(
                    "配比方向与 " + clash.id().asString() + " 相同 会永远打平");
        }
        Map<String, Object> node = flexNode(draft);
        return persistThenApply(recipe.id(), kind,
                afterSave -> RecipeFileStore.replaceTarget(file, oldTarget, nodePath, node, afterSave),
                () -> {
                    Object replaced = RecipeSourceIndex.instance()
                            .removeSource(kind, recipe.id(), file, nodePath);
                    removeMenuRecipe(replaced);
                    if (replaced != null) {
                        removeRuntime(kind, recipe.id());
                    }
                    if (old != null) {
                        FoodRecipeRegistry.instance().removeFlex(oldId);
                        FoodRecipeRegistry.instance().removeMenuFlex(old);
                        RecipeSourceIndex.instance().remove(old);
                    }
                    RecipeSourceIndex.instance().restore(kind, recipe.id(), file, nodePath);
                    boolean currentDuplicate = RecipeSourceIndex.instance()
                            .hasOtherSource(kind, recipe.id(), file, nodePath);
                    FoodRecipeRegistry.instance().registerMenuFlex(recipe);
                    RecipeSourceIndex.instance()
                            .put(kind, recipe.id(), file, nodePath, recipe, currentDuplicate);
                    if (!currentDuplicate) {
                        FoodRecipeRegistry.instance().registerFlex(recipe);
                        for (Key ingredient : recipe.perfect().keySet()) {
                            ApplianceFoodRegistry.instance().register(recipe.cook(), ingredient);
                        }
                    }
                });
    }

    public static String validate(ChoppingRecipeDraft draft) {
        String idError = validateId(draft.id());
        if (idError != null) {
            return idError;
        }
        if (isTakenId(draft.id(), draft.originalId(),
                k -> RecipeSourceIndex.instance().hasSource(RecipeSourceIndex.Kind.CHOPPING, k))) {
            return "该 id 已被占用";
        }
        if (draft.input() == null) {
            return "还没设置原料";
        }
        if (draft.results().isEmpty()) {
            return "至少要有一个成品";
        }
        return null;
    }

    public static String validate(TeapotRecipeDraft draft) {
        String idError = validateId(draft.id());
        if (idError != null) {
            return idError;
        }
        if (isTakenId(draft.id(), draft.originalId(),
                k -> RecipeSourceIndex.instance().hasSource(RecipeSourceIndex.Kind.TEAPOT, k))) {
            return "该 id 已被占用";
        }
        if (draft.fluid() == null) {
            return "还没设置液体";
        }
        if (!FoodRecipeRegistry.instance().hasTeapotLiquid(draft.fluid())) {
            return "该液体没在 teapot_liquid 里登记";
        }
        if (draft.input() == null) {
            return "还没设置原料";
        }
        if (draft.result() == null) {
            return "还没设置成品";
        }
        // 成品没有茶杯模型的话解析期会被跳过 这里提前拦下来 免得存了个不生效的配方
        if (!FoodRecipeRegistry.instance().hasTeaCup(draft.result())) {
            return "该成品没在 tea_cup 里定义模型";
        }
        return null;
    }

    public static CompletableFuture<String> saveChopping(ChoppingRecipeDraft draft) {
        String error = validate(draft);
        if (error != null) {
            return CompletableFuture.completedFuture(error);
        }
        ChoppingBoardRecipe recipe = draft.toRecipe();
        Key oldId = draft.originalId();
        Path file = resolveFile(draft.originalRecipe(), RecipeFileStore::defaultChoppingFile);
        if (file == null) {
            return CompletableFuture.completedFuture("找不到可写入的配方文件");
        }
        String oldNode = RecipeSourceIndex.instance().nodePath(draft.originalRecipe());
        RecipeFileStore.SourceTarget oldTarget = RecipeSourceIndex.instance().target(draft.originalRecipe());
        String nodePath = resolveNodePath(oldNode, RecipeFileStore.choppingSections(), recipe.id());
        ChoppingBoardRecipe oldChopping = draft.originalRecipe();
        RecipeSourceIndex.Kind kind = RecipeSourceIndex.Kind.CHOPPING;
        Map<String, Object> node = choppingNode(draft);
        return persistThenApply(recipe.id(), kind,
                afterSave -> RecipeFileStore.replaceTarget(file, oldTarget, nodePath, node, afterSave),
                () -> {
                    Object replaced = RecipeSourceIndex.instance()
                            .removeSource(kind, recipe.id(), file, nodePath);
                    removeMenuRecipe(replaced);
                    if (replaced != null) {
                        removeRuntime(kind, recipe.id());
                    }
                    if (oldChopping != null) {
                        FoodRecipeRegistry.instance().removeChopping(oldId);
                        FoodRecipeRegistry.instance().removeMenuChopping(oldChopping);
                        RecipeSourceIndex.instance().remove(oldChopping);
                    }
                    RecipeSourceIndex.instance().restore(kind, recipe.id(), file, nodePath);
                    boolean duplicate = RecipeSourceIndex.instance()
                            .hasOtherSource(kind, recipe.id(), file, nodePath);
                    FoodRecipeRegistry.instance().registerMenuChopping(recipe);
                    RecipeSourceIndex.instance().put(kind, recipe.id(), file, nodePath, recipe, duplicate);
                    if (!duplicate) {
                        FoodRecipeRegistry.instance().registerChopping(recipe);
                        ApplianceFoodRegistry.instance()
                                .register(ApplianceType.CHOPPING_BOARD, recipe.input());
                    }
                    if (oldChopping != null && !oldChopping.input().equals(recipe.input())) {
                        dropInputIfUnused(ApplianceType.CHOPPING_BOARD, oldChopping.input(),
                                RecipeEditService::choppingUses);
                    }
                });
    }

    public static CompletableFuture<String> saveTeapot(TeapotRecipeDraft draft) {
        String error = validate(draft);
        if (error != null) {
            return CompletableFuture.completedFuture(error);
        }
        TeapotRecipe recipe = draft.toRecipe();
        Key oldId = draft.originalId();
        Path file = resolveFile(draft.originalRecipe(), RecipeFileStore::defaultTeapotFile);
        if (file == null) {
            return CompletableFuture.completedFuture("找不到可写入的配方文件");
        }
        String oldNode = RecipeSourceIndex.instance().nodePath(draft.originalRecipe());
        RecipeFileStore.SourceTarget oldTarget = RecipeSourceIndex.instance().target(draft.originalRecipe());
        String nodePath = resolveNodePath(oldNode, RecipeFileStore.teapotSections(), recipe.id());
        TeapotRecipe oldTeapot = draft.originalRecipe();
        RecipeSourceIndex.Kind kind = RecipeSourceIndex.Kind.TEAPOT;
        Map<String, Object> node = teapotNode(draft);
        return persistThenApply(recipe.id(), kind,
                afterSave -> RecipeFileStore.replaceTarget(file, oldTarget, nodePath, node, afterSave),
                () -> {
                    Object replaced = RecipeSourceIndex.instance()
                            .removeSource(kind, recipe.id(), file, nodePath);
                    removeMenuRecipe(replaced);
                    if (replaced != null) {
                        removeRuntime(kind, recipe.id());
                    }
                    if (oldTeapot != null) {
                        FoodRecipeRegistry.instance().removeTeapot(oldId);
                        FoodRecipeRegistry.instance().removeMenuTeapot(oldTeapot);
                        RecipeSourceIndex.instance().remove(oldTeapot);
                    }
                    RecipeSourceIndex.instance().restore(kind, recipe.id(), file, nodePath);
                    boolean duplicate = RecipeSourceIndex.instance()
                            .hasOtherSource(kind, recipe.id(), file, nodePath);
                    FoodRecipeRegistry.instance().registerMenuTeapot(recipe);
                    RecipeSourceIndex.instance().put(kind, recipe.id(), file, nodePath, recipe, duplicate);
                    if (!duplicate) {
                        FoodRecipeRegistry.instance().registerTeapot(recipe);
                        ApplianceFoodRegistry.instance().register(ApplianceType.TEAPOT, recipe.input());
                    }
                    if (oldTeapot != null && !oldTeapot.input().equals(recipe.input())) {
                        dropInputIfUnused(ApplianceType.TEAPOT, oldTeapot.input(),
                                RecipeEditService::teapotUses);
                    }
                });
    }

    private static CompletableFuture<String> persistThenApply(Key id, RecipeSourceIndex.Kind kind,
                                                               SaveAction save, Runnable hotUpdate) {
        CompletableFuture<String> result = new CompletableFuture<>();
        try {
            runAsync(() -> {
                try {
                    save.run(() -> RecipeSourceIndex.instance().afterCurrentLoad(kind, () -> {
                        try {
                            hotUpdate.run();
                            result.complete(null);
                        } catch (RuntimeException error) {
                            RecipeFileStore.logFailure("热更新", id, error);
                            result.complete(HOT_UPDATE_FAILED);
                        }
                    }));
                } catch (Exception error) {
                    RecipeFileStore.logFailure("保存", id, error);
                    result.complete(SAVE_FAILED);
                }
            });
        } catch (RuntimeException error) {
            RecipeFileStore.logFailure("保存", id, error);
            result.complete(SAVE_FAILED);
        }
        return result;
    }

    public static CompletableFuture<Boolean> deleteChopping(ChoppingBoardRecipe recipe) {
        return deleteRecipe(recipe, recipe.id());
    }

    public static CompletableFuture<Boolean> deleteTeapot(TeapotRecipe recipe) {
        return deleteRecipe(recipe, recipe.id());
    }

    public static CompletableFuture<Boolean> deleteAccurate(AccurateFoodRecipe recipe) {
        return deleteRecipe(recipe, recipe.id());
    }

    public static CompletableFuture<Boolean> deleteFlex(FlexFoodRecipe recipe) {
        return deleteRecipe(recipe, recipe.id());
    }

    // YAML 写盘成功才整理运行时 因此失败时玩家看到的食谱与配置都保持原状
    private static CompletableFuture<Boolean> deleteRecipe(Object recipe, Key id) {
        Path file = RecipeSourceIndex.instance().get(recipe);
        RecipeFileStore.SourceTarget target = RecipeSourceIndex.instance().target(recipe);
        RecipeSourceIndex.Kind kind = RecipeSourceIndex.instance().kind(recipe);
        if (file == null || target == null || kind == null) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        runAsync(() -> {
            try {
                if (!RecipeFileStore.deleteTarget(file, target)) {
                    RecipeFileStore.logFailure("删除", id,
                            new IllegalStateException("找不到该食谱在配置中的真实来源"));
                    result.complete(false);
                    return;
                }
            } catch (Exception e) {
                RecipeFileStore.logFailure("删除", id, e);
                result.complete(false);
                return;
            }

            RecipeSourceIndex sourceIndex = RecipeSourceIndex.instance();
            sourceIndex.markDeleted(kind, id, file, target);
            sourceIndex.afterCurrentLoad(kind, () -> {
                try {
                    applyHotDeletion(recipe, kind, id, file, target);
                    result.complete(true);
                } catch (RuntimeException error) {
                    // 文件已经删除 此处失败不能再向玩家谎报配置仍在 下次重载会按磁盘结果收敛
                    RecipeFileStore.logFailure("热删除", id, error);
                    result.complete(true);
                } finally {
                    RecipeSourceIndex.instance().restore(kind, id, file, target);
                }
            });
        });
        return result;
    }

    private static void applyHotDeletion(Object selected, RecipeSourceIndex.Kind kind, Key id,
                                         Path file, RecipeFileStore.SourceTarget target) {
        RecipeSourceIndex sourceIndex = RecipeSourceIndex.instance();
        Object current = sourceIndex.removeSource(kind, id, file, target);
        sourceIndex.remove(selected);
        removeMenuRecipe(selected);
        if (current != selected) {
            removeMenuRecipe(current);
        }
        removeRuntime(kind, id);

        List<Object> remaining = sourceIndex.recipes(kind, id);
        boolean duplicate = remaining.size() > 1;
        for (Object candidate : remaining) {
            sourceIndex.duplicate(candidate, duplicate);
        }
        if (remaining.size() == 1) {
            registerRuntime(remaining.getFirst(), kind);
        }

        if (selected instanceof AccurateFoodRecipe accurate) {
            dropInputIfUnused(accurate.cook(), accurate.input(),
                    input -> accurateUses(accurate.cook(), input));
        } else if (selected instanceof ChoppingBoardRecipe chopping) {
            dropInputIfUnused(ApplianceType.CHOPPING_BOARD, chopping.input(), RecipeEditService::choppingUses);
        } else if (selected instanceof TeapotRecipe teapot) {
            dropInputIfUnused(ApplianceType.TEAPOT, teapot.input(), RecipeEditService::teapotUses);
        }
    }

    private static void removeMenuRecipe(Object recipe) {
        FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
        if (recipe instanceof AccurateFoodRecipe accurate) {
            registry.removeMenuAccurate(accurate);
        } else if (recipe instanceof FlexFoodRecipe flex) {
            registry.removeMenuFlex(flex);
        } else if (recipe instanceof ChoppingBoardRecipe chopping) {
            registry.removeMenuChopping(chopping);
        } else if (recipe instanceof TeapotRecipe teapot) {
            registry.removeMenuTeapot(teapot);
        }
    }

    private static void removeRuntime(RecipeSourceIndex.Kind kind, Key id) {
        FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
        switch (kind) {
            case ACCURATE -> registry.removeAccurate(id);
            case POT_FLEX -> registry.removeFlex(ApplianceType.POT, id);
            case STOCK_FLEX -> registry.removeFlex(ApplianceType.STOCKPOT, id);
            case CHOPPING -> registry.removeChopping(id);
            case TEAPOT -> registry.removeTeapot(id);
        }
    }

    private static void registerRuntime(Object candidate, RecipeSourceIndex.Kind kind) {
        FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
        switch (kind) {
            case ACCURATE -> {
                AccurateFoodRecipe recipe = (AccurateFoodRecipe) candidate;
                registry.registerAccurate(recipe);
                ApplianceFoodRegistry.instance().register(recipe.cook(), recipe.input());
            }
            case POT_FLEX, STOCK_FLEX -> {
                FlexFoodRecipe recipe = (FlexFoodRecipe) candidate;
                // 解除重复后仍需遵守模糊配方的同方向冲突规则
                if (registry.findSameDirection(recipe) == null) {
                    registry.registerFlex(recipe);
                    for (Key ingredient : recipe.perfect().keySet()) {
                        ApplianceFoodRegistry.instance().register(recipe.cook(), ingredient);
                    }
                }
            }
            case CHOPPING -> {
                ChoppingBoardRecipe recipe = (ChoppingBoardRecipe) candidate;
                registry.registerChopping(recipe);
                ApplianceFoodRegistry.instance().register(ApplianceType.CHOPPING_BOARD, recipe.input());
            }
            case TEAPOT -> {
                TeapotRecipe recipe = (TeapotRecipe) candidate;
                registry.registerTeapot(recipe);
                ApplianceFoodRegistry.instance().register(ApplianceType.TEAPOT, recipe.input());
            }
        }
    }

    // 同一原料可能被同器具的多条配方共用 还有人用就不能摘白名单
    // 换了原料时 旧原料若没有同类配方再用 就从下锅白名单里摘掉
    // stillUsed 只查本类配方 各厨具的白名单互相独立
    private static void dropInputIfUnused(ApplianceType cook, Key input, Predicate<Key> stillUsed) {
        if (!stillUsed.test(input)) {
            ApplianceFoodRegistry.instance().unregister(cook, input);
        }
    }

    private static boolean accurateUses(ApplianceType cook, Key input) {
        for (AccurateFoodRecipe r : FoodRecipeRegistry.instance().accurateRecipes(cook)) {
            if (r.input().equals(input)) {
                return true;
            }
        }
        return false;
    }

    private static boolean choppingUses(Key input) {
        for (ChoppingBoardRecipe r : FoodRecipeRegistry.instance().choppingRecipes()) {
            if (r.input().equals(input)) {
                return true;
            }
        }
        return false;
    }

    private static boolean teapotUses(Key input) {
        for (TeapotRecipe r : FoodRecipeRegistry.instance().teapotRecipes()) {
            if (r.input().equals(input)) {
                return true;
            }
        }
        return false;
    }

    // 已有配方原地改写 新配方才去问 CE 要资源包目录 后者在没加载任何包时会抛
    private static Path resolveFile(Object existingRecipe, Supplier<Path> fallback) {
        Path known = existingRecipe == null ? null : RecipeSourceIndex.instance().get(existingRecipe);
        if (known != null) {
            return known;
        }
        try {
            return fallback.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String resolveNodePath(String oldNode, String[] sectionAliases, Key id) {
        if (oldNode == null || oldNode.isBlank()) {
            return sectionAliases[0] + "." + id.asString();
        }
        int separator = oldNode.lastIndexOf('.');
        return (separator < 0 ? sectionAliases[0] : oldNode.substring(0, separator))
                + "." + id.asString();
    }

    // 磁盘 IO 走 CE 的 async 调度器 folia 上 Bukkit 调度器整条链路都是废的
    // 汤底登记 桶 -> 液面 先热更注册表再异步落盘 show 传 null 用默认水面
    public static String saveSoupBase(Key bucket, Key show) {
        if (bucket == null) {
            return "汤底物品 id 不能为空";
        }
        Key model = show == null ? SoupBaseRegistry.DEFAULT_SHOW : show;
        SoupBaseRegistry.instance().register(bucket, model);
        runAsync(() -> {
            try {
                RecipeFileStore.writeSoupBase(bucket, model);
            } catch (Exception e) {
                RecipeFileStore.logFailure("保存汤底", bucket, e);
            }
        });
        return null;
    }

    public static void deleteSoupBase(Key bucket) {
        if (bucket == null) {
            return;
        }
        SoupBaseRegistry.instance().remove(bucket);
        runAsync(() -> {
            try {
                RecipeFileStore.deleteSoupBase(bucket);
            } catch (Exception e) {
                RecipeFileStore.logFailure("删除汤底", bucket, e);
            }
        });
    }

    private static void runAsync(Runnable task) {
        CraftEngine.instance().scheduler().executeAsync(task);
    }

    private static Map<String, Object> accurateNode(AccurateFoodRecipe recipe) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("require", recipe.input().asString());
        List<WeightedResult> results = recipe.results();
        // 单成品写成标量 与手写配置的常见写法一致 其余写 物品 权重 列表
        if (results.size() == 1) {
            node.put("result", results.getFirst().key().asString());
        } else {
            List<String> lines = new ArrayList<>(results.size());
            for (WeightedResult r : results) {
                lines.add(r.key().asString() + " " + r.weight());
            }
            node.put("result", lines);
        }
        node.put("cook", recipe.cook().name().toLowerCase());
        if (recipe.resultCount() > 1) {
            node.put("result_count", recipe.resultCount());
        }
        if (recipe.cook() == ApplianceType.MILLSTONE && recipe.rotations() > 0) {
            node.put("rotations", recipe.rotations());
        }
        if (!recipe.lore().isEmpty()) {
            node.put("lore", new ArrayList<>(recipe.lore()));
        }
        return node;
    }

    // 用 draft 而不是 FlexFoodRecipe 建节点 后者的 perfect 是 Map.copyOf 已丢失编辑顺序
    // values 是模型 id 前缀 没设就整条不写 解析期会退回展示物品本身
    private static Map<String, Object> choppingNode(ChoppingRecipeDraft draft) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("require", draft.input().asString());
        node.put("stage", draft.stage());
        if (draft.modelPrefix() != null) {
            node.put("values", draft.modelPrefix());
        }
        node.put("mode", draft.mode().name().toLowerCase());
        node.put("result", choppingResults(draft.results()));
        if (draft.mode() == ChoppingMode.SINGLE_EXTRA && !draft.extras().isEmpty()) {
            node.put("extra", choppingResults(draft.extras()));
        }
        return node;
    }

    // 配置写法是 物品 数量 权重 的字符串列表
    private static List<String> choppingResults(List<ChoppingResult> results) {
        List<String> out = new ArrayList<>(results.size());
        for (ChoppingResult r : results) {
            out.add(r.key().asString() + " " + r.count() + " " + r.weight());
        }
        return out;
    }

    private static Map<String, Object> teapotNode(TeapotRecipeDraft draft) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("fluid", draft.fluid().asString());
        node.put("require", draft.input().asString() + " " + draft.ingredientCount());
        node.put("result", draft.result().asString() + " " + draft.resultCount());
        node.put("time", draft.time());
        return node;
    }

    private static Map<String, Object> flexNode(FlexRecipeDraft draft) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("result", draft.result().asString());
        Map<String, Object> perfect = new LinkedHashMap<>();
        for (Map.Entry<Key, Integer> e : draft.perfect().entrySet()) {
            perfect.put(e.getKey().asString(), e.getValue());
        }
        node.put("perfect", perfect);
        if (!draft.liquids().isEmpty()) {
            List<String> liquids = new ArrayList<>(draft.liquids().size());
            for (Key k : draft.liquids()) {
                liquids.add(k.asString());
            }
            node.put("liquid", liquids);
        }
        // 省略即空手盛出 不写空值免得配置里多一行没意义的 carrier:
        if (draft.carrier() != null) {
            node.put("carrier", draft.carrier().asString());
        }
        return node;
    }
}
