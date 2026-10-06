package net.kaleidoscope.cookery.recipe.edit;

import net.kaleidoscope.cookery.api.ItemTags;
import net.kaleidoscope.cookery.recipe.AccurateFoodRecipe;
import net.kaleidoscope.cookery.recipe.ApplianceFoodRegistry;
import net.kaleidoscope.cookery.recipe.FoodGroups;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

// UI 编辑配方的落地点 保存与删除均先落盘再更新运行时
public final class RecipeEditService {
    private static final String SAVE_FAILED = "配置文件写入失败，原食谱未修改";
    private static final String HOT_UPDATE_FAILED = "配置文件已写入，运行时更新失败，请重载配置";
    private static final ConcurrentLinkedQueue<Runnable> IO_QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean DRAINING = new AtomicBoolean();
    private static final AtomicBoolean WAITING_FOR_CE = new AtomicBoolean();
    private static final java.util.Set<CompletableFuture<?>> PENDING = ConcurrentHashMap.newKeySet();
    private static volatile boolean closed;

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
        return saveRecipe(recipe.id(), draft.originalId(), recipe, draft.originalRecipe(),
                RecipeSourceIndex.Kind.ACCURATE, RecipeFileStore.accurateSections(),
                RecipeFileStore::defaultAccurateFile, accurateNode(recipe));
    }

    public static CompletableFuture<String> saveFlex(FlexRecipeDraft draft) {
        String error = validate(draft);
        if (error != null) {
            return CompletableFuture.completedFuture(error);
        }
        FlexFoodRecipe recipe = draft.toRecipe();
        FlexFoodRecipe old = draft.originalRecipe();
        RecipeSourceIndex.Kind kind = recipe.cook() == ApplianceType.STOCKPOT
                ? RecipeSourceIndex.Kind.STOCK_FLEX : RecipeSourceIndex.Kind.POT_FLEX;
        String clash = findDirectionClash(recipe, old, kind);
        if (clash != null) {
            return CompletableFuture.completedFuture(clash);
        }
        return saveRecipe(recipe.id(), draft.originalId(), recipe, old,
                kind, RecipeFileStore.flexSections(recipe.cook()),
                () -> RecipeFileStore.defaultFlexFile(recipe.cook()), flexNode(draft));
    }

    // 余弦尺度无关 方向相同的两道菜会永远打平 落盘前先拦下来
    // 本身就是重复来源的那条不算冲突 它压根不会进运行时注册表
    private static String findDirectionClash(FlexFoodRecipe recipe, FlexFoodRecipe old,
                                             RecipeSourceIndex.Kind kind) {
        boolean duplicate = RecipeSourceIndex.instance().recipes(kind, recipe.id()).stream()
                .anyMatch(existing -> existing != old);
        if (duplicate) {
            return null;
        }
        FlexFoodRecipe clash = FoodRecipeRegistry.instance().findSameDirection(recipe, old);
        return clash == null ? null : "配比方向与 " + clash.id().asString() + " 相同 会永远打平";
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
        return saveRecipe(recipe.id(), draft.originalId(), recipe, draft.originalRecipe(),
                RecipeSourceIndex.Kind.CHOPPING, RecipeFileStore.choppingSections(),
                RecipeFileStore::defaultChoppingFile, choppingNode(draft));
    }

    public static CompletableFuture<String> saveTeapot(TeapotRecipeDraft draft) {
        String error = validate(draft);
        if (error != null) {
            return CompletableFuture.completedFuture(error);
        }
        TeapotRecipe recipe = draft.toRecipe();
        return saveRecipe(recipe.id(), draft.originalId(), recipe, draft.originalRecipe(),
                RecipeSourceIndex.Kind.TEAPOT, RecipeFileStore.teapotSections(),
                RecipeFileStore::defaultTeapotFile, teapotNode(draft));
    }

    // 四类配方保存走同一条路 差异只在 kind sections 默认文件与写盘节点
    // 运行时的增删都按 kind 与实际类型分派 见 removeRuntime registerRuntime removeMenuRecipe
    // old 为空表示新建 此时索引里查不到旧来源 各步都会自然跳过
    private static CompletableFuture<String> saveRecipe(Key id, Key oldId, Object recipe, Object old,
                                                        RecipeSourceIndex.Kind kind, String[] sections,
                                                        Supplier<Path> defaultFile,
                                                        Map<String, Object> node) {
        if (old != null && RecipeSourceIndex.instance().target(old) == null) {
            return CompletableFuture.completedFuture("食谱已被修改或重载，请重新打开编辑菜单");
        }
        Path file = resolveFile(old, defaultFile);
        if (file == null) {
            return CompletableFuture.completedFuture("找不到可写入的配方文件");
        }
        RecipeSourceIndex index = RecipeSourceIndex.instance();
        RecipeFileStore.SourceTarget oldTarget = index.target(old);
        String nodePath = resolveNodePath(index.nodePath(old), sections, id);
        Map<String, Object> changes = changedFields(old == null ? Map.of() : recipeNode(old), node);
        if (old != null && changes.isEmpty() && id.equals(oldId)) {
            return CompletableFuture.completedFuture(null);
        }
        AtomicReference<RecipeFileStore.PatchedRecipe> saved = new AtomicReference<>();
        AtomicReference<Runnable> relocate = new AtomicReference<>(() -> {});
        return persistThenApply(id, kind,
                afterSave -> {
                    saved.set(RecipeFileStore.patchTarget(file, oldTarget, nodePath, id, changes,
                            () -> CraftEngine.instance().isReloading()));
                    relocate.set(index.prepareRelocations(file));
                    afterSave.run();
                },
                () -> {
                    RecipeFileStore.PatchedRecipe patched = saved.get();
                    Object effectiveRecipe = savedRecipe(recipe, patched.expanded());
                    relocate.get().run();
                    Object replaced = index.removeSource(kind, id, file, patched.target());
                    removeMenuRecipe(replaced);
                    if (replaced != null) {
                        removeRuntime(kind, id);
                    }
                    if (old != null) {
                        removeRuntime(kind, oldId);
                        removeMenuRecipe(old);
                        index.remove(old);
                    }
                    index.restore(kind, id, file, patched.target());
                    boolean duplicate = index.hasOtherSource(kind, id, file, patched.target());
                    registerMenuRecipe(effectiveRecipe);
                    index.put(kind, id, file, patched.target(), effectiveRecipe, duplicate);
                    if (!duplicate) {
                        registerRuntime(effectiveRecipe, kind);
                    }
                    // 放在重新登记之后 原料没变时新配方已占着它 stillUsed 恒真 不会误摘
                    if (old != null) {
                        dropInputIfUnused(old);
                    }
                });
    }

    private static CompletableFuture<String> persistThenApply(Key id, RecipeSourceIndex.Kind kind,
                                                               SaveAction save, Runnable hotUpdate) {
        CompletableFuture<String> result = pendingResult();
        try {
            runAsync(() -> {
                try (var resourceLease = CraftEngine.instance().resourceOperations().acquire()) {
                    // Keep one CE generation across expansion, disk replacement, source relocation and publication.
                    save.run(() -> {
                        try {
                            if (closed) return;
                            FoodRecipeRegistry.instance().atomicUpdate(hotUpdate);
                            result.complete(null);
                        } catch (RuntimeException error) {
                            RecipeFileStore.logFailure("热更新", id, error);
                            result.complete(HOT_UPDATE_FAILED);
                        }
                    });
                } catch (Exception error) {
                    RecipeFileStore.logFailure("保存", id, error);
                    result.complete(error instanceof net.momirealms.craftengine.core.plugin.ResourceOperationCoordinator.BusyException
                            ? "CraftEngine 正在更新资源，请稍后保存"
                            : error instanceof java.io.IOException ? error.getMessage() : SAVE_FAILED);
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
        CompletableFuture<Boolean> result = pendingResult();
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
            FoodRecipeRegistry.instance().afterConfigurationLoad(() -> sourceIndex.afterCurrentLoad(kind, () -> {
                try {
                    if (closed) return;
                    FoodRecipeRegistry.instance().atomicUpdate(() -> applyHotDeletion(recipe, kind, id, file, target));
                    result.complete(true);
                } catch (RuntimeException error) {
                    // 文件已经删除 此处失败不能再向玩家谎报配置仍在 下次重载会按磁盘结果收敛
                    RecipeFileStore.logFailure("热删除", id, error);
                    result.complete(true);
                } finally {
                    RecipeSourceIndex.instance().restore(kind, id, file, target);
                }
            }));
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

        dropInputIfUnused(selected);
    }

    private static void registerMenuRecipe(Object recipe) {
        FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
        if (recipe instanceof AccurateFoodRecipe accurate) {
            registry.registerMenuAccurate(accurate);
        } else if (recipe instanceof FlexFoodRecipe flex) {
            registry.registerMenuFlex(flex);
        } else if (recipe instanceof ChoppingBoardRecipe chopping) {
            registry.registerMenuChopping(chopping);
        } else if (recipe instanceof TeapotRecipe teapot) {
            registry.registerMenuTeapot(teapot);
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

    // 模糊配方没有单一原料这一维 它的白名单是从 perfect 整体反推的 这里不参与
    private static void dropInputIfUnused(Object recipe) {
        if (recipe instanceof AccurateFoodRecipe accurate) {
            dropInputIfUnused(accurate.cook(), accurate.input(),
                    input -> accurateUses(accurate.cook(), input));
        } else if (recipe instanceof ChoppingBoardRecipe chopping) {
            dropInputIfUnused(ApplianceType.CHOPPING_BOARD, chopping.input(), RecipeEditService::choppingUses);
        } else if (recipe instanceof TeapotRecipe teapot) {
            dropInputIfUnused(ApplianceType.TEAPOT, teapot.input(), RecipeEditService::teapotUses);
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

    // 磁盘写成功才动运行时 失败则两边都保持原状
    public static CompletableFuture<String> saveFoodGroup(Key tag, List<Key> members, FoodGroups.Kind kind) {
        String error = validateFoodGroup(tag, members);
        if (error != null) {
            return CompletableFuture.completedFuture(error);
        }
        List<Key> snapshot = List.copyOf(members);
        CompletableFuture<String> result = pendingResult();
        runAsync(() -> {
            try {
                RecipeFileStore.writeFoodGroup(tag, snapshot, kind);
            } catch (Exception e) {
                RecipeFileStore.logFailure("保存食材分组", tag, e);
                result.complete(SAVE_FAILED);
                return;
            }
            // 解析器是 add 合并语义 这里必须整体替换 否则删掉的成员残留到下次重载
            FoodRecipeRegistry.instance().afterConfigurationLoad(() -> {
                if (closed) return;
                FoodRecipeRegistry.instance().atomicUpdate(() -> {
                    ItemTags.instance().register(tag, snapshot.stream().map(Key::asString).toList());
                    FoodGroups.instance().put(tag, kind);
                });
                result.complete(null);
            });
        });
        return result;
    }

    public static CompletableFuture<Boolean> deleteFoodGroup(Key tag) {
        CompletableFuture<Boolean> result = pendingResult();
        runAsync(() -> {
            try {
                RecipeFileStore.deleteFoodGroup(tag);
            } catch (Exception e) {
                RecipeFileStore.logFailure("删除食材分组", tag, e);
                result.complete(false);
                return;
            }
            FoodRecipeRegistry.instance().afterConfigurationLoad(() -> {
                if (closed) return;
                FoodRecipeRegistry.instance().atomicUpdate(() -> {
                    FoodGroups.instance().remove(tag);
                    ItemTags.instance().remove(tag);
                });
                result.complete(true);
            });
        });
        return result;
    }

    // 标签 id 会被当成 YamlConfiguration 的二级路径 带点号会写坏文件
    public static String validateFoodGroup(Key tag, List<Key> members) {
        if (tag.value().indexOf('.') >= 0 || tag.namespace().indexOf('.') >= 0) {
            return "标签 id 不能包含点号";
        }
        if (members.isEmpty()) {
            return "分组里至少要有一个物品";
        }
        return null;
    }

    private static void runAsync(Runnable task) {
        if (closed) throw new IllegalStateException("插件已关闭");
        if (IO_QUEUE.size() >= 256) throw new IllegalStateException("保存队列已满，请稍后重试");
        IO_QUEUE.add(task);
        scheduleDrain();
    }

    private static void scheduleDrain() {
        if (WAITING_FOR_CE.get() && configurationBusy()) return;
        if (closed || !DRAINING.compareAndSet(false, true)) return;
        try {
            CraftEngine.instance().scheduler().executeAsync(RecipeEditService::drain);
        } catch (RuntimeException error) {
            DRAINING.set(false);
            close();
            throw error;
        }
    }

    private static void drain() {
        try {
            while (!closed) {
                // TemplateManager is mutable during CE's load. Validate only against a completed generation.
                if (configurationBusy()) {
                    if (WAITING_FOR_CE.compareAndSet(false, true)) {
                        CompletableFuture.delayedExecutor(25, TimeUnit.MILLISECONDS,
                                CraftEngine.instance().scheduler().async()).execute(() -> {
                            WAITING_FOR_CE.set(false);
                            scheduleDrain();
                        });
                    }
                    return;
                }
                Runnable task = IO_QUEUE.poll();
                if (task == null) return;
                task.run();
            }
        } finally {
            DRAINING.set(false);
            if (!closed && !IO_QUEUE.isEmpty() && !configurationBusy()) scheduleDrain();
        }
    }

    private static boolean configurationBusy() {
        return CraftEngine.instance().resourceOperations().isBusy()
                || CraftEngine.instance().isReloading() || FoodRecipeRegistry.instance().isConfigurationLoading();
    }

    private static <T> CompletableFuture<T> pendingResult() {
        CompletableFuture<T> result = new CompletableFuture<>();
        PENDING.add(result);
        result.whenComplete((value, error) -> PENDING.remove(result));
        return result;
    }

    public static boolean isClosed() { return closed; }

    /** Does not interrupt an atomic write already in progress. */
    public static void close() {
        closed = true;
        IO_QUEUE.clear();
        PENDING.forEach(future -> future.completeExceptionally(new IllegalStateException("插件已关闭")));
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
        if (recipe.cookingTime() > 0) node.put("cooking_time", recipe.cookingTime());
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
        if (draft.stage() > 0) node.put("stage", draft.stage());
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
        if (draft.time() > 0) node.put("cooking_time", draft.time());
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
        // 两张组表默认生效 只写关掉的那一项 免得每条配方都多两行恒真的开关
        if (!draft.useEquivalentFoods()) {
            node.put("use_equivalent_foods", false);
        }
        if (!draft.useSeasonings()) {
            node.put("use_seasonings", false);
        }
        if (draft.cookingTime() > 0) node.put("cooking_time", draft.cookingTime());
        if (draft.stirFryCount() > 0) node.put("stir_fry_count", draft.stirFryCount());
        return node;
    }

    private static Map<String, Object> recipeNode(Object recipe) {
        if (recipe instanceof AccurateFoodRecipe value) return accurateNode(value);
        if (recipe instanceof FlexFoodRecipe value) return flexNode(FlexRecipeDraft.editing(value));
        if (recipe instanceof ChoppingBoardRecipe value) return choppingNode(ChoppingRecipeDraft.editing(value));
        if (recipe instanceof TeapotRecipe value) return teapotNode(TeapotRecipeDraft.editing(value));
        return Map.of();
    }

    private static Map<String, Object> changedFields(Map<String, Object> old, Map<String, Object> next) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>(old.keySet());
        keys.addAll(next.keySet());
        Map<String, Object> changes = new LinkedHashMap<>();
        for (String key : keys) {
            if (!java.util.Objects.equals(old.get(key), next.get(key))) changes.put(key, next.get(key));
        }
        return changes;
    }

    private static int savedInt(Map<String, Object> expanded, String field, int fallback) {
        for (String alias : RecipePatchWriter.aliases(field)) {
            Object value = expanded.get(alias);
            if (value instanceof Number number) return number.intValue();
            if (value instanceof String text) {
                try { return Integer.parseInt(text); } catch (NumberFormatException ignored) { }
            }
        }
        return fallback;
    }

    private static Object savedRecipe(Object recipe, Map<String, Object> expanded) {
        net.momirealms.craftengine.core.plugin.config.ConfigSection section =
                net.momirealms.craftengine.core.plugin.config.ConfigSection.of("saved_recipe", expanded);
        if (recipe instanceof AccurateFoodRecipe value) {
            List<WeightedResult> results = new ArrayList<>();
            Object raw = section.get("result");
            if (raw instanceof List<?> list) {
                for (Object result : list) {
                    String[] parts = result.toString().trim().split("\\s+", 2);
                    results.add(new WeightedResult(Key.of(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : 100));
                }
            } else results.add(new WeightedResult(section.getNonNullIdentifier("result"), 100));
            return new AccurateFoodRecipe(value.id(), section.getNonNullIdentifier("require"), results, value.cook(),
                    savedInt(expanded, "rotations", 0), Math.max(1, savedInt(expanded, "result_count", 1)), section.getStringList("lore"),
                    savedInt(expanded, "cooking_time", 0));
        }
        if (recipe instanceof FlexFoodRecipe value) {
            Map<Key, Integer> perfect = new LinkedHashMap<>();
            var weights = section.getSection("perfect");
            if (weights != null) {
                for (String key : weights.keySet()) perfect.put(Key.of(key), weights.getInt(key, 1));
            } else {
                for (String raw : section.getStringList("perfect")) {
                    String[] parts = raw.trim().split("\\s+", 2);
                    perfect.put(Key.of(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
                }
            }
            String carrier = section.getString("carrier", (String) null);
            return FlexFoodRecipe.of(value.id(), section.getNonNullIdentifier("result"), value.cook(), perfect,
                    section.getStringList("liquid").stream().map(Key::of).toList(),
                    carrier == null || carrier.isEmpty() || "minecraft:air".equals(carrier) ? null : Key.of(carrier),
                    section.getBoolean(new String[]{"use_equivalent_foods", "use-equivalent-foods"}, true),
                    section.getBoolean(new String[]{"use_seasonings", "use-seasonings"}, true),
                    savedInt(expanded, "cooking_time", 0), savedInt(expanded, "stir_fry_count", 0));
        }
        if (recipe instanceof TeapotRecipe value) {
            String[] input = section.getNonNullString("require").trim().split("\\s+", 2);
            String[] output = section.getNonNullString("result").trim().split("\\s+", 2);
            return new TeapotRecipe(value.id(), section.getNonNullIdentifier("fluid"), Key.of(input[0]),
                    input.length > 1 ? Integer.parseInt(input[1]) : 1, Key.of(output[0]),
                    output.length > 1 ? Integer.parseInt(output[1]) : 1, savedInt(expanded, "time", 200));
        }
        if (recipe instanceof ChoppingBoardRecipe value) {
            int stage = section.getInt("stage", 1);
            String prefix = section.getString("values", (String) null);
            List<String> models = new ArrayList<>();
            if (prefix != null && !prefix.isEmpty()) {
                for (int index = 0; index < stage; index++) models.add(prefix + "/" + index);
            }
            return new ChoppingBoardRecipe(value.id(), section.getNonNullIdentifier("require"), stage, models,
                    ChoppingMode.valueOf(section.getString("mode", "single").toUpperCase(java.util.Locale.ROOT)),
                    savedChoppingResults(section.get("result")), savedChoppingResults(section.get("extra")));
        }
        return recipe;
    }

    private static List<ChoppingResult> savedChoppingResults(Object raw) {
        if (raw == null) return List.of();
        List<?> values = raw instanceof List<?> list ? list : List.of(raw);
        List<ChoppingResult> results = new ArrayList<>();
        for (Object value : values) {
            String[] parts = value.toString().trim().split("\\s+", 3);
            results.add(new ChoppingResult(Key.of(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : 1,
                    parts.length > 2 ? Integer.parseInt(parts[2]) : 100));
        }
        return results;
    }
}
