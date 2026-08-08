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
import java.util.function.Predicate;
import java.util.function.Supplier;

// UI 编辑配方的落地点 校验通过后先热更内存注册表 再异步把 YAML 写回
// 内存注册表用的是并发容器 所以先生效后落盘不需要额外同步 也不需要触发 CE 重载
public final class RecipeEditService {
    private RecipeEditService() {
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
                k -> FoodRecipeRegistry.instance().findAccurateById(k) != null)) {
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
                k -> FoodRecipeRegistry.instance().findFlexById(k) != null)) {
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

    // 保存精准配方 返回错误文案 null 表示已受理
    public static String saveAccurate(AccurateRecipeDraft draft) {
        String error = validate(draft);
        if (error != null) {
            return error;
        }
        AccurateFoodRecipe recipe = draft.toRecipe();
        Key oldId = draft.originalId();
        // 目标文件先定下来 定不下来就整个不动 免得内存生效了却写不回配置
        Path file = resolveFile(oldId, RecipeFileStore::defaultAccurateFile);
        if (file == null) {
            return "找不到可写入的配方文件";
        }
        if (oldId != null) {
            AccurateFoodRecipe old = FoodRecipeRegistry.instance().findAccurateById(oldId);
            FoodRecipeRegistry.instance().removeAccurate(oldId);
            if (old != null && !old.input().equals(recipe.input())) {
                dropInputIfUnused(old.cook(), old.input(), k -> accurateUses(old.cook(), k));
            }
        }
        FoodRecipeRegistry.instance().registerAccurate(recipe);
        ApplianceFoodRegistry.instance().register(recipe.cook(), recipe.input());

        RecipeSourceIndex.instance().put(recipe.id(), file);
        Map<String, Object> node = accurateNode(recipe);
        runAsync(() -> {
            try {
                if (oldId != null && !oldId.equals(recipe.id())) {
                    RecipeFileStore.delete(file, RecipeFileStore.accurateSections(), oldId);
                    RecipeSourceIndex.instance().remove(oldId);
                }
                RecipeFileStore.write(file, RecipeFileStore.accurateSections(), recipe.id(), node);
            } catch (Exception e) {
                RecipeFileStore.logFailure("保存", recipe.id(), e);
            }
        });
        return null;
    }

    public static String saveFlex(FlexRecipeDraft draft) {
        String error = validate(draft);
        if (error != null) {
            return error;
        }
        FlexFoodRecipe recipe = draft.toRecipe();
        Key oldId = draft.originalId();
        String[] sections = RecipeFileStore.flexSections(recipe.cook());
        // 目标文件先定下来 定不下来就整个不动 免得内存生效了却写不回配置
        Path file = resolveFile(oldId, () -> RecipeFileStore.defaultFlexFile(recipe.cook()));
        if (file == null) {
            return "找不到可写入的配方文件";
        }
        FlexFoodRecipe old = oldId == null ? null : FoodRecipeRegistry.instance().findFlexById(oldId);
        if (oldId != null) {
            FoodRecipeRegistry.instance().removeFlex(oldId);
        }
        // 余弦是尺度无关的 理想向量方向相同的两道菜会永远打平 保存前拦下来
        FlexFoodRecipe clash = FoodRecipeRegistry.instance().findSameDirection(recipe);
        if (clash != null) {
            if (old != null) {
                FoodRecipeRegistry.instance().registerFlex(old);
            }
            return "配比方向与 " + clash.id().asString() + " 相同 会永远打平";
        }
        FoodRecipeRegistry.instance().registerFlex(recipe);
        // 能配出菜的料就该能下锅 与 FoodRecipeManager.parseFlexRecipe 保持一致
        // 漏了这步 菜单新建的配方要等下次 reload 才能把食材放进锅里
        for (Key ingredient : recipe.perfect().keySet()) {
            ApplianceFoodRegistry.instance().register(recipe.cook(), ingredient);
        }

        RecipeSourceIndex.instance().put(recipe.id(), file);
        Map<String, Object> node = flexNode(draft);
        runAsync(() -> {
            try {
                if (oldId != null && !oldId.equals(recipe.id())) {
                    RecipeFileStore.delete(file, sections, oldId);
                    RecipeSourceIndex.instance().remove(oldId);
                }
                RecipeFileStore.write(file, sections, recipe.id(), node);
            } catch (Exception e) {
                RecipeFileStore.logFailure("保存", recipe.id(), e);
            }
        });
        return null;
    }

    public static String validate(ChoppingRecipeDraft draft) {
        String idError = validateId(draft.id());
        if (idError != null) {
            return idError;
        }
        if (isTakenId(draft.id(), draft.originalId(),
                k -> FoodRecipeRegistry.instance().findChoppingById(k) != null)) {
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
                k -> FoodRecipeRegistry.instance().findTeapotById(k) != null)) {
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

    public static String saveChopping(ChoppingRecipeDraft draft) {
        String error = validate(draft);
        if (error != null) {
            return error;
        }
        ChoppingBoardRecipe recipe = draft.toRecipe();
        Key oldId = draft.originalId();
        Path file = resolveFile(oldId, RecipeFileStore::defaultChoppingFile);
        if (file == null) {
            return "找不到可写入的配方文件";
        }
        ChoppingBoardRecipe oldChopping = oldId == null ? null
                : FoodRecipeRegistry.instance().findChoppingById(oldId);
        if (oldId != null) {
            FoodRecipeRegistry.instance().removeChopping(oldId);
        }
        FoodRecipeRegistry.instance().registerChopping(recipe);
        ApplianceFoodRegistry.instance().register(ApplianceType.CHOPPING_BOARD, recipe.input());
        if (oldChopping != null && !oldChopping.input().equals(recipe.input())) {
            dropInputIfUnused(ApplianceType.CHOPPING_BOARD, oldChopping.input(),
                    RecipeEditService::choppingUses);
        }
        RecipeSourceIndex.instance().put(recipe.id(), file);

        Map<String, Object> node = choppingNode(draft);
        String[] sections = RecipeFileStore.choppingSections();
        runAsync(() -> {
            try {
                if (oldId != null && !oldId.equals(recipe.id())) {
                    RecipeFileStore.delete(file, sections, oldId);
                    RecipeSourceIndex.instance().remove(oldId);
                }
                RecipeFileStore.write(file, sections, recipe.id(), node);
            } catch (Exception e) {
                RecipeFileStore.logFailure("保存", recipe.id(), e);
            }
        });
        return null;
    }

    public static String saveTeapot(TeapotRecipeDraft draft) {
        String error = validate(draft);
        if (error != null) {
            return error;
        }
        TeapotRecipe recipe = draft.toRecipe();
        Key oldId = draft.originalId();
        Path file = resolveFile(oldId, RecipeFileStore::defaultTeapotFile);
        if (file == null) {
            return "找不到可写入的配方文件";
        }
        TeapotRecipe oldTeapot = oldId == null ? null
                : FoodRecipeRegistry.instance().findTeapotById(oldId);
        if (oldId != null) {
            FoodRecipeRegistry.instance().removeTeapot(oldId);
        }
        FoodRecipeRegistry.instance().registerTeapot(recipe);
        ApplianceFoodRegistry.instance().register(ApplianceType.TEAPOT, recipe.input());
        if (oldTeapot != null && !oldTeapot.input().equals(recipe.input())) {
            dropInputIfUnused(ApplianceType.TEAPOT, oldTeapot.input(), RecipeEditService::teapotUses);
        }
        RecipeSourceIndex.instance().put(recipe.id(), file);

        Map<String, Object> node = teapotNode(draft);
        String[] sections = RecipeFileStore.teapotSections();
        runAsync(() -> {
            try {
                if (oldId != null && !oldId.equals(recipe.id())) {
                    RecipeFileStore.delete(file, sections, oldId);
                    RecipeSourceIndex.instance().remove(oldId);
                }
                RecipeFileStore.write(file, sections, recipe.id(), node);
            } catch (Exception e) {
                RecipeFileStore.logFailure("保存", recipe.id(), e);
            }
        });
        return null;
    }

    public static void deleteChopping(ChoppingBoardRecipe recipe) {
        FoodRecipeRegistry.instance().removeChopping(recipe.id());
        dropInputIfUnused(ApplianceType.CHOPPING_BOARD, recipe.input(), RecipeEditService::choppingUses);
        Path file = RecipeSourceIndex.instance().get(recipe.id());
        if (file == null) {
            return;
        }
        RecipeSourceIndex.instance().remove(recipe.id());
        runAsync(() -> {
            try {
                RecipeFileStore.delete(file, RecipeFileStore.choppingSections(), recipe.id());
            } catch (Exception e) {
                RecipeFileStore.logFailure("删除", recipe.id(), e);
            }
        });
    }

    public static void deleteTeapot(TeapotRecipe recipe) {
        FoodRecipeRegistry.instance().removeTeapot(recipe.id());
        dropInputIfUnused(ApplianceType.TEAPOT, recipe.input(), RecipeEditService::teapotUses);
        Path file = RecipeSourceIndex.instance().get(recipe.id());
        if (file == null) {
            return;
        }
        RecipeSourceIndex.instance().remove(recipe.id());
        runAsync(() -> {
            try {
                RecipeFileStore.delete(file, RecipeFileStore.teapotSections(), recipe.id());
            } catch (Exception e) {
                RecipeFileStore.logFailure("删除", recipe.id(), e);
            }
        });
    }

    public static void deleteAccurate(AccurateFoodRecipe recipe) {
        FoodRecipeRegistry.instance().removeAccurate(recipe.id());
        dropInputIfUnused(recipe.cook(), recipe.input(), k -> accurateUses(recipe.cook(), k));
        Path file = resolveFile(recipe.id(), RecipeFileStore::defaultAccurateFile);
        RecipeSourceIndex.instance().remove(recipe.id());
        if (file == null) {
            return;
        }
        runAsync(() -> {
            try {
                RecipeFileStore.delete(file, RecipeFileStore.accurateSections(), recipe.id());
            } catch (Exception e) {
                RecipeFileStore.logFailure("删除", recipe.id(), e);
            }
        });
    }

    public static void deleteFlex(FlexFoodRecipe recipe) {
        FoodRecipeRegistry.instance().removeFlex(recipe.id());
        String[] sections = RecipeFileStore.flexSections(recipe.cook());
        Path file = resolveFile(recipe.id(), () -> RecipeFileStore.defaultFlexFile(recipe.cook()));
        RecipeSourceIndex.instance().remove(recipe.id());
        if (file == null) {
            return;
        }
        runAsync(() -> {
            try {
                RecipeFileStore.delete(file, sections, recipe.id());
            } catch (Exception e) {
                RecipeFileStore.logFailure("删除", recipe.id(), e);
            }
        });
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
    private static Path resolveFile(Key existingId, Supplier<Path> fallback) {
        Path known = existingId == null ? null : RecipeSourceIndex.instance().get(existingId);
        if (known != null) {
            return known;
        }
        try {
            return fallback.get();
        } catch (RuntimeException e) {
            return null;
        }
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
