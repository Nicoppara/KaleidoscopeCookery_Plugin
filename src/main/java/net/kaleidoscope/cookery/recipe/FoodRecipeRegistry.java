package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

// 配方运行期注册表 数据来自 CraftEngine 配置加载
// 外部插件追加注册须在自身 enable 阶段完成 之后配置重载会清空并重新填充
@SuppressWarnings("unused")
public final class FoodRecipeRegistry {
    private record AccurateKey(ApplianceType cook, Key input) {}

    // 必需食材齐全后允许任意杂料 默认不再用相似度阈值拒绝成菜
    private static final double DEFAULT_MIN_FLEX_SCORE = 0.0;

    private static final FoodRecipeRegistry INSTANCE = new FoodRecipeRegistry();
    private final Object publicationLock = new Object();
    private final ThreadLocal<State> writer = new ThreadLocal<>();
    private final ThreadLocal<State> reader = new ThreadLocal<>();
    private volatile State published = new State().freeze();
    private volatile State loading;
    private volatile boolean closed;
    private boolean loadFailed;
    private final List<Runnable> afterLoad = new ArrayList<>();
    private final List<Runnable> loadEdits = new ArrayList<>();
    private long generation;
    private long loadSequence;
    private RawGroupSource groupBackup;

    private record RawGroupSource(Map<Key, Set<String>> tags, List<Key> equivalents, List<Key> seasonings) {
        static RawGroupSource capture() {
            net.kaleidoscope.cookery.api.ItemTags tags = net.kaleidoscope.cookery.api.ItemTags.instance();
            Map<Key, Set<String>> members = new LinkedHashMap<>();
            for (Key key : tags.keys()) members.put(key, tags.members(key));
            return new RawGroupSource(Map.copyOf(members), List.copyOf(FoodGroups.instance().equivalentTags()),
                    List.copyOf(FoodGroups.instance().seasoningTags()));
        }

        void restore() {
            net.kaleidoscope.cookery.api.ItemTags registry = net.kaleidoscope.cookery.api.ItemTags.instance();
            for (Key key : registry.keys()) if (!tags.containsKey(key)) registry.remove(key);
            tags.forEach(registry::register);
            FoodGroups.instance().equivalentTags(equivalents);
            FoodGroups.instance().seasoningTags(seasonings);
        }
    }

    private record TeapotKey(Key fluid, Key input) {}
    private record FlexAnchor(ApplianceType cook, boolean canonical, Key input) {}

    private static final class State {
        boolean frozen;
        List<FlexFoodRecipe> flexRecipes = new ArrayList<>();
        List<FlexFoodRecipe> menuFlexRecipes = new ArrayList<>();
        double minFlexScore = DEFAULT_MIN_FLEX_SCORE;
        List<AccurateFoodRecipe> accurateRecipes = new ArrayList<>();
        List<AccurateFoodRecipe> menuAccurateRecipes = new ArrayList<>();
        Map<AccurateKey, AccurateFoodRecipe> accurateIndex = new LinkedHashMap<>();
        List<ChoppingBoardRecipe> choppingRecipes = new ArrayList<>();
        List<ChoppingBoardRecipe> menuChoppingRecipes = new ArrayList<>();
        List<TeapotRecipe> teapotRecipes = new ArrayList<>();
        List<TeapotRecipe> menuTeapotRecipes = new ArrayList<>();
        Map<Key, TeapotLiquid> teapotLiquids = new LinkedHashMap<>();
        TeapotLiquid defaultLiquid;
        Map<Key, TeaCup> teaCups = new LinkedHashMap<>();
        Map<Key, TeaCup> teaCupsByItem = new LinkedHashMap<>();
        Map<ApplianceType, Set<Key>> allowed = new EnumMap<>(ApplianceType.class);
        Map<Key, Key> soupBases = new LinkedHashMap<>();
        Map<Key, Key> configuredCarriers = new LinkedHashMap<>();
        List<FlexFoodRecipe> carrierRecipes;
        Map<Key, Key> carriers = Map.of();
        Map<TeapotKey, TeapotRecipe> teapotIndex = Map.of();
        Map<Key, ChoppingBoardRecipe> choppingIndex = Map.of();
        FoodGroupView groups = FoodGroupView.empty();
        Map<FlexFoodRecipe, FlexMatcher.Ideal> compiled = Map.of();
        Map<FlexAnchor, BitSet> flexAnchors = Map.of();

        State copy() {
            State copy = new State();
            copy.flexRecipes.addAll(flexRecipes); copy.menuFlexRecipes.addAll(menuFlexRecipes);
            copy.accurateRecipes.addAll(accurateRecipes); copy.menuAccurateRecipes.addAll(menuAccurateRecipes);
            copy.accurateIndex.putAll(accurateIndex);
            copy.choppingRecipes.addAll(choppingRecipes); copy.menuChoppingRecipes.addAll(menuChoppingRecipes);
            copy.teapotRecipes.addAll(teapotRecipes); copy.menuTeapotRecipes.addAll(menuTeapotRecipes);
            copy.teapotLiquids.putAll(teapotLiquids); copy.defaultLiquid = defaultLiquid;
            copy.teaCups.putAll(teaCups); copy.teaCupsByItem.putAll(teaCupsByItem);
            allowed.forEach((type, values) -> copy.allowed.put(type, new LinkedHashSet<>(values)));
            copy.soupBases.putAll(soupBases); copy.configuredCarriers.putAll(configuredCarriers);
            copy.carrierRecipes = carrierRecipes;
            copy.minFlexScore = minFlexScore;
            return copy;
        }

        State freeze() {
            return freeze(FoodGroupView.capture());
        }

        State freeze(FoodGroupView matchingGroups) {
            flexRecipes = sourceOrdered(flexRecipes); menuFlexRecipes = sourceOrdered(menuFlexRecipes);
            accurateRecipes = sourceOrdered(accurateRecipes); menuAccurateRecipes = sourceOrdered(menuAccurateRecipes);
            choppingRecipes = sourceOrdered(choppingRecipes); menuChoppingRecipes = sourceOrdered(menuChoppingRecipes);
            teapotRecipes = sourceOrdered(teapotRecipes); menuTeapotRecipes = sourceOrdered(menuTeapotRecipes);
            Map<AccurateKey, AccurateFoodRecipe> accurate = new LinkedHashMap<>();
            for (AccurateFoodRecipe recipe : accurateRecipes) accurate.putIfAbsent(new AccurateKey(recipe.cook(), recipe.input()), recipe);
            accurateIndex = Map.copyOf(accurate);
            Map<TeapotKey, TeapotRecipe> teas = new LinkedHashMap<>();
            for (TeapotRecipe recipe : teapotRecipes) teas.putIfAbsent(new TeapotKey(recipe.fluid(), recipe.input()), recipe);
            teapotIndex = Map.copyOf(teas);
            Map<Key, ChoppingBoardRecipe> chopping = new LinkedHashMap<>();
            for (ChoppingBoardRecipe recipe : choppingRecipes) chopping.putIfAbsent(recipe.input(), recipe);
            choppingIndex = Map.copyOf(chopping);
            teapotLiquids = Collections.unmodifiableMap(new LinkedHashMap<>(teapotLiquids));
            teaCups = Map.copyOf(teaCups); teaCupsByItem = Map.copyOf(teaCupsByItem);
            Map<ApplianceType, Set<Key>> allow = new EnumMap<>(ApplianceType.class);
            allowed.forEach((type, values) -> allow.put(type, Set.copyOf(values)));
            allowed = Collections.unmodifiableMap(allow);
            soupBases = Map.copyOf(soupBases); configuredCarriers = Map.copyOf(configuredCarriers);
            Map<Key, Key> carrierMap = new LinkedHashMap<>();
            for (FlexFoodRecipe recipe : carrierRecipes == null ? flexRecipes : carrierRecipes) {
                if (recipe.carrier() != null) carrierMap.putIfAbsent(recipe.result(), recipe.carrier());
            }
            carrierMap.putAll(configuredCarriers); carriers = Map.copyOf(carrierMap);
            groups = matchingGroups;
            Map<FlexFoodRecipe, FlexMatcher.Ideal> ideals = new IdentityHashMap<>();
            Map<FlexAnchor, BitSet> anchors = new HashMap<>();
            for (int index = 0; index < flexRecipes.size(); index++) {
                FlexFoodRecipe recipe = flexRecipes.get(index);
                FlexMatcher.Ideal ideal = FlexMatcher.compile(recipe, groups);
                ideals.put(recipe, ideal);
                boolean canonical = recipe.useEquivalentFoods() && groups.hasEquivalents();
                Key anchor = ideal.weights().keySet().stream().min(Comparator.comparing(Key::asString)).orElse(null);
                if (anchor != null) anchors.computeIfAbsent(new FlexAnchor(recipe.cook(), canonical, anchor), ignored -> new BitSet()).set(index);
            }
            compiled = Collections.unmodifiableMap(ideals); flexAnchors = Map.copyOf(anchors);
            frozen = true;
            return this;
        }
    }

    private State state() {
        State editing = writer.get();
        if (editing != null) return editing;
        State reading = reader.get();
        return reading == null ? published : reading;
    }

    /** Pin whitelist and recipe reads to one generation for a complete input operation. */
    public <T> T readSnapshot(java.util.function.Supplier<T> action) {
        if (reader.get() != null || writer.get() != null) return action.get();
        reader.set(published);
        try { return action.get(); }
        finally { reader.remove(); }
    }

    public boolean isConfigurationWriter() { return loading != null && writer.get() == loading; }
    public boolean isConfigurationLoading() { return loading != null; }

    public long generation() { synchronized (publicationLock) { return generation; } }

    /** All related registry mutations become visible in one immutable publication. */
    public void atomicUpdate(Runnable action) { update(() -> { action.run(); return null; }); }

    private <T> T update(java.util.function.Supplier<T> action) {
        if (writer.get() != null) return action.get();
        synchronized (publicationLock) {
            if (closed) throw new IllegalStateException("Cookery recipe registry is closed");
            State next = published.copy();
            writer.set(next);
            try {
                T result = action.get();
                published = loading == null ? next.freeze() : next.freeze(published.groups);
                generation++;
                if (loading != null) loadEdits.add(() -> action.get());
                return result;
            } finally { writer.remove(); }
        }
    }

    void beginConfigurationLoad() {
        if (closed) return;
        if (loading != null) {
            // CE serializes resource operations; a new BEGIN means the previous operation ended.
            try { abortConfigurationLoad(configurationLoadToken()); }
            catch (RuntimeException failure) {
                var plugin = net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin.instance();
                if (plugin != null) plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "恢复上次配方加载时有编辑回调失败；继续重新加载。", failure);
            }
        }
        synchronized (publicationLock) {
            if (closed) return;
            if (loading != null) {
                // A previous failed CE load may have skipped END before the recovery poll ran.
                if (groupBackup != null) groupBackup.restore();
                net.kaleidoscope.cookery.recipe.edit.RecipeSourceIndex.instance().finishConfigurationLoad(false);
            }
            net.kaleidoscope.cookery.recipe.edit.RecipeSourceIndex.instance().beginConfigurationLoad();
            groupBackup = RawGroupSource.capture();
            loading = new State(); loading.minFlexScore = published.minFlexScore;
            loadSequence++;
            loadFailed = false; loadEdits.clear();
        }
    }

    public void configurationUpdate(Runnable action) {
        synchronized (publicationLock) {
            if (closed) return;
            if (loading == null) { atomicUpdate(action); return; }
            State previous = writer.get(); writer.set(loading);
            try { action.run(); }
            catch (RuntimeException | Error failure) { loadFailed = true; throw failure; }
            finally { if (previous == null) writer.remove(); else writer.set(previous); }
        }
    }

    void finishConfigurationLoad() {
        finishConfigurationLoad(0, false);
    }

    long configurationLoadToken() {
        synchronized (publicationLock) { return loadSequence; }
    }

    boolean hasConfigurationLoad(long token) {
        synchronized (publicationLock) { return loading != null && loadSequence == token; }
    }

    void abortConfigurationLoad(long token) {
        finishConfigurationLoad(token, true);
    }

    private void finishConfigurationLoad(long token, boolean failed) {
        List<Runnable> callbacks;
        Throwable completionFailure = null;
        synchronized (publicationLock) {
            if (loading == null || token != 0 && token != loadSequence) return;
            loadFailed |= failed;
            State next = loadFailed ? published.copy() : loading;
            writer.set(next);
            try {
                if (loadFailed && groupBackup != null) groupBackup.restore();
                // Failed loads already include live edits in published; replaying appends them twice.
                if (!loadFailed) for (Runnable edit : loadEdits) edit.run();
                State frozen = loadFailed ? next.freeze(published.groups) : next.freeze();
                net.kaleidoscope.cookery.recipe.edit.RecipeSourceIndex.instance().finishConfigurationLoad(!loadFailed);
                net.kaleidoscope.cookery.recipe.edit.RecipeTemplateReplayGuard.finishConfigurationLoad(!loadFailed);
                published = frozen;
                generation++;
            } catch (RuntimeException | Error failure) {
                completionFailure = failure;
                try {
                    if (groupBackup != null) groupBackup.restore();
                    net.kaleidoscope.cookery.recipe.edit.RecipeSourceIndex.instance().finishConfigurationLoad(false);
                    net.kaleidoscope.cookery.recipe.edit.RecipeTemplateReplayGuard.finishConfigurationLoad(false);
                } catch (RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            } finally {
                writer.remove(); loading = null; loadEdits.clear(); groupBackup = null;
                callbacks = List.copyOf(afterLoad); afterLoad.clear();
            }
        }
        try {
            net.kaleidoscope.cookery.recipe.edit.RecipeSourceIndex.instance().finishInterruptedLoads();
        } catch (RuntimeException failure) {
            if (completionFailure == null) completionFailure = failure; else completionFailure.addSuppressed(failure);
        }
        for (Runnable callback : callbacks) {
            try { callback.run(); }
            catch (RuntimeException | Error failure) {
                if (completionFailure == null) completionFailure = failure;
                else completionFailure.addSuppressed(failure);
            }
        }
        if (completionFailure instanceof RuntimeException runtime) throw runtime;
        if (completionFailure instanceof Error error) throw error;
    }

    private record SourcePosition<T>(int position, int ordinal, T recipe) {}

    /** Keep each file's YAML emission order even when CE loads separate factory segments out of order. */
    private static <T> List<T> sourceOrdered(List<T> recipes) {
        var sources = net.kaleidoscope.cookery.recipe.edit.RecipeSourceIndex.instance();
        Map<java.nio.file.Path, List<SourcePosition<T>>> files = new LinkedHashMap<>();
        for (int index = 0; index < recipes.size(); index++) {
            T recipe = recipes.get(index);
            var target = sources.target(recipe);
            if (target == null || target.sourceOrdinal() < 0) continue;
            var file = sources.get(recipe);
            if (file != null) files.computeIfAbsent(file, ignored -> new ArrayList<>())
                    .add(new SourcePosition<>(index, target.sourceOrdinal(), recipe));
        }
        List<T> ordered = new ArrayList<>(recipes);
        for (var positions : files.values()) {
            List<SourcePosition<T>> sorted = new ArrayList<>(positions);
            sorted.sort(Comparator.comparingInt(SourcePosition<T>::ordinal));
            for (int index = 0; index < positions.size(); index++) {
                ordered.set(positions.get(index).position(), sorted.get(index).recipe());
            }
        }
        return List.copyOf(ordered);
    }

    void failConfigurationLoad() {
        synchronized (publicationLock) { if (loading != null) loadFailed = true; }
    }

    public void afterConfigurationLoad(Runnable action) {
        synchronized (publicationLock) {
            if (closed) return;
            if (loading != null) { afterLoad.add(action); return; }
        }
        action.run();
    }

    public void close() {
        synchronized (publicationLock) {
            closed = true;
            if (loading != null) {
                if (groupBackup != null) groupBackup.restore();
                net.kaleidoscope.cookery.recipe.edit.RecipeSourceIndex.instance().finishConfigurationLoad(false);
            }
            net.kaleidoscope.cookery.recipe.edit.RecipeTemplateReplayGuard.finishConfigurationLoad(false);
            loading = null;
            groupBackup = null;
            loadEdits.clear();
            afterLoad.clear();
        }
    }

    public void refreshGroupView() {
        synchronized (publicationLock) { if (closed || loading != null) return; }
        atomicUpdate(() -> {});
    }

    private static FlexMatcher.Match match(State view, ApplianceType type, List<Key> inputs, Key liquid) {
        if (inputs.isEmpty() || view.flexRecipes.isEmpty()) return null;
        if (!view.frozen) return FlexMatcher.bestMatch(view.flexRecipes, view.minFlexScore, type, inputs, liquid,
                FoodGroupView.capture(), Map.of()); // Configuration builder only.
        BitSet candidates = new BitSet();
        for (Key input : inputs) {
            BitSet raw = view.flexAnchors.get(new FlexAnchor(type, false, input));
            if (raw != null) candidates.or(raw);
            BitSet canonical = view.flexAnchors.get(new FlexAnchor(type, true, view.groups.canonical(input)));
            if (canonical != null) candidates.or(canonical);
        }
        List<FlexFoodRecipe> recipes = new ArrayList<>(candidates.cardinality());
        for (int index = candidates.nextSetBit(0); index >= 0; index = candidates.nextSetBit(index + 1)) recipes.add(view.flexRecipes.get(index));
        return FlexMatcher.bestMatch(recipes, view.minFlexScore, type, inputs, liquid, view.groups, view.compiled);
    }

    void registerAllowed(ApplianceType type, Key key) { atomicUpdate(() -> state().allowed.computeIfAbsent(type, ignored -> new LinkedHashSet<>()).add(key)); }
    void unregisterAllowed(ApplianceType type, Key key) { atomicUpdate(() -> { Set<Key> allowed = state().allowed.get(type); if (allowed != null) allowed.remove(key); }); }
    void clearAllowed(ApplianceType type) { atomicUpdate(() -> state().allowed.remove(type)); }
    boolean isAllowed(ApplianceType type, Key key) {
        State view = state();
        Set<Key> allowed = view.allowed.getOrDefault(type, Set.of());
        if (allowed.contains(key)) return true;
        FoodGroupView groups = writer.get() == null ? view.groups : FoodGroupView.capture();
        if (!type.usesFlexRecipes()) return false;
        if (groups.isSeasoning(key)) return true;
        Key canonical = groups.canonical(key);
        if (canonical.equals(key)) return false;
        for (Key candidate : allowed) if (canonical.equals(groups.canonical(candidate))) return true;
        return false;
    }
    void registerSoupBase(Key bucket, Key model) { atomicUpdate(() -> state().soupBases.put(bucket, model)); }
    void clearSoupBases() { atomicUpdate(() -> state().soupBases.clear()); }
    void removeSoupBase(Key bucket) { atomicUpdate(() -> state().soupBases.remove(bucket)); }
    Map<Key, Key> soupBases() { return state().soupBases; }
    void registerConfiguredCarrier(Key item, Key carrier) { atomicUpdate(() -> state().configuredCarriers.put(item, carrier)); }
    void clearConfiguredCarriers() { atomicUpdate(() -> state().configuredCarriers.clear()); }
    void rebuildCarriers(Iterable<FlexFoodRecipe> recipes) {
        List<FlexFoodRecipe> supplied = new ArrayList<>();
        recipes.forEach(supplied::add);
        List<FlexFoodRecipe> immutable = List.copyOf(supplied);
        atomicUpdate(() -> state().carrierRecipes = immutable);
    }
    Key carrierOf(Key item) { return item == null ? null : state().carriers.get(item); }
    boolean hasCarriers() { return !state().carriers.isEmpty(); }

    private FoodRecipeRegistry() {
    }

    public static FoodRecipeRegistry instance() {
        return INSTANCE;
    }

    /** Metadata-only lookup; unlike findAccurate it neither rolls output nor creates an Item. */
    public AccurateFoodRecipe findAccurateRecipe(ApplianceType type, Key input) {
        State view = state();
        return view.accurateIndex.get(new AccurateKey(type, input));
    }

    public CookingPlan planAccurate(ApplianceType type, Key input, int defaultWork) {
        try {
            AccurateFoodRecipe recipe = findAccurateRecipe(type, input);
            if (recipe == null) return CookingPlan.unmatched(defaultWork);
            int configured = type == ApplianceType.MILLSTONE ? recipe.rotations() : recipe.cookingTime();
            WeightedResult chosen = WeightedPicker.pick(recipe.results(), WeightedResult::weight);
            if (chosen == null) return CookingPlan.invalid();
            return new CookingPlan(true, true, recipe.id(), configured > 0 ? configured : Math.max(1, defaultWork),
                    1, null, List.of(new CookingPlan.Output(chosen.key(), recipe.resultCount(), recipe.lore(), null)), List.of());

        } catch (RuntimeException malformedRecipe) {
            return CookingPlan.invalid();
        }
    }

    public CookingPlan planFlex(ApplianceType type, List<Key> inputs, Key liquid, int defaultWork) {
        try {
            State view = state();
            FlexMatcher.Match match = match(view, type, inputs, liquid);
            if (match == null) return CookingPlan.unmatched(defaultWork);
            FlexFoodRecipe recipe = match.recipe();
            int configured = type == ApplianceType.POT ? recipe.stirFryCount() : recipe.cookingTime();
            return new CookingPlan(true, true, recipe.id(), configured > 0 ? configured : Math.max(1, defaultWork),
                    1, recipe.carrier(), List.of(new CookingPlan.Output(recipe.result(), match.portions(), List.of(), match.quality())), List.of());

        } catch (RuntimeException malformedRecipe) {
            return CookingPlan.invalid();
        }
    }

    public CookingPlan planTeapot(Key fluid, Key input) {
        try {
            TeapotRecipe recipe = findTeapot(fluid, input);
            if (recipe == null) return CookingPlan.unmatched(200);
            return new CookingPlan(true, true, recipe.id(), Math.max(1, recipe.time()), recipe.ingredientCount(), null,
                    List.of(new CookingPlan.Output(recipe.result(), recipe.resultCount(), List.of(), null)), List.of());

        } catch (RuntimeException malformedRecipe) {
            return CookingPlan.invalid();
        }
    }

    public CookingPlan planChopping(Key input) {
        try {
            ChoppingBoardRecipe recipe = findChoppingByInput(input);
            if (recipe == null) return CookingPlan.unmatched(1);
            List<CookingPlan.Output> outputs = new ArrayList<>();
            switch (recipe.mode()) {
                case SINGLE -> planChoppingOutput(outputs, WeightedPicker.pick(recipe.results(), ChoppingResult::weight));
                case SINGLE_EXTRA -> {
                    planChoppingOutput(outputs, WeightedPicker.pick(recipe.results(), ChoppingResult::weight));
                    for (ChoppingResult extra : recipe.extras()) if (WeightedPicker.roll(extra.weight())) planChoppingOutput(outputs, extra);
                }
                case MULTI_RANDOM -> {
                    for (ChoppingResult result : recipe.results()) if (WeightedPicker.roll(result.weight())) planChoppingOutput(outputs, result);
                    if (outputs.isEmpty()) planChoppingOutput(outputs, WeightedPicker.pick(recipe.results(), ChoppingResult::weight));
                }
            }
            if (outputs.isEmpty()) return CookingPlan.invalid();
            return new CookingPlan(true, true, recipe.id(), Math.max(1, recipe.stage()), 1, null, outputs, recipe.values());

        } catch (RuntimeException malformedRecipe) {
            return CookingPlan.invalid();
        }
    }

    private static void planChoppingOutput(List<CookingPlan.Output> outputs, ChoppingResult result) {
        if (result != null) outputs.add(new CookingPlan.Output(result.key(), Math.max(1, result.count()), List.of(), null));
    }

    public int totalRecipeCount() {
        return flexRecipeCount() + accurateRecipeCount() + choppingRecipeCount() + teapotRecipeCount();
    }

    public int recipeCount(ApplianceType cook) {
        int count = flexRecipeCount(cook) + accurateRecipeCount(cook);
        if (cook == ApplianceType.CHOPPING_BOARD) {
            count += choppingRecipeCount();
        } else if (cook == ApplianceType.TEAPOT) {
            count += teapotRecipeCount();
        }
        return count;
    }

    public int flexRecipeCount() {
        State view = state();
        return view.flexRecipes.size();
    }

    public int flexRecipeCount(ApplianceType cook) {
        State view = state();
        int count = 0;
        for (FlexFoodRecipe recipe : view.flexRecipes) {
            if (recipe.cook() == cook) {
                count++;
            }
        }
        return count;
    }

    public int accurateRecipeCount() {
        State view = state();
        return view.accurateRecipes.size();
    }

    public int accurateRecipeCount(ApplianceType cook) {
        State view = state();
        int count = 0;
        for (AccurateFoodRecipe recipe : view.accurateRecipes) {
            if (recipe.cook() == cook) {
                count++;
            }
        }
        return count;
    }

    public int choppingRecipeCount() {
        State view = state();
        return view.choppingRecipes.size();
    }

    public int teapotRecipeCount() {
        State view = state();
        return view.teapotRecipes.size();
    }

    public int teapotLiquidCount() {
        State view = state();
        return view.teapotLiquids.size();
    }

    public int teaCupCount() {
        State view = state();
        return view.teaCups.size();
    }

    public void minFlexScore(double value) {
        atomicUpdate(() -> {
            State view = state();
            view.minFlexScore = value;

        });
    }

    // 方向相同即余弦恒等 两道菜会永远打平 注册前查重
    public FlexFoodRecipe findSameDirection(FlexFoodRecipe candidate) {
        return findSameDirection(candidate, null);
    }

    public FlexFoodRecipe findSameDirection(FlexFoodRecipe candidate, FlexFoodRecipe excluded) {
        State view = state();
        for (FlexFoodRecipe r : view.flexRecipes) {
            if (r == excluded) {
                continue;
            }
            if (r.cook() != candidate.cook() || r.perfect().size() != candidate.perfect().size()) {
                continue;
            }
            // 汤底不重叠的两条配方在匹配时就被过滤开了 理想配比再像也不会打平
            // 水底饺子和岩浆底生煎馒头就是同一个向量 但永远碰不到一起
            if (!liquidsOverlap(r.liquids(), candidate.liquids())) {
                continue;
            }
            Double scale = null;
            boolean same = true;
            for (Map.Entry<Key, Integer> e : candidate.perfect().entrySet()) {
                Integer other = r.perfect().get(e.getKey());
                if (other == null) {
                    same = false;
                    break;
                }
                double ratio = (double) other / e.getValue();
                if (scale == null) {
                    scale = ratio;
                } else if (Math.abs(scale - ratio) > 1e-6) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return r;
            }
        }
        return null;
    }

    // 任一方不限汤底就一定会相遇 否则要有交集才算相遇
    private static boolean liquidsOverlap(List<Key> a, List<Key> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return true;
        }
        for (Key k : a) {
            if (b.contains(k)) {
                return true;
            }
        }
        return false;
    }

    public void registerFlex(FlexFoodRecipe r) {
        atomicUpdate(() -> {
            State view = state();
            view.flexRecipes.add(r);
            view.carrierRecipes = null;
            // Carrier lookup is rebuilt with the same snapshot.

        });
    }

    public void registerMenuFlex(FlexFoodRecipe r) {
        atomicUpdate(() -> {
            State view = state();
            view.menuFlexRecipes.add(r);

        });
    }

    public void registerAccurate(AccurateFoodRecipe r) {
        atomicUpdate(() -> {
            State view = state();
            view.accurateRecipes.add(r);
            // 精确配方按 器具 加 输入 唯一确定 注册期建好索引 别在热路径上全表扫
            view.accurateIndex.putIfAbsent(new AccurateKey(r.cook(), r.input()), r);

        });
    }

    public void registerMenuAccurate(AccurateFoodRecipe r) {
        atomicUpdate(() -> {
            State view = state();
            view.menuAccurateRecipes.add(r);

        });
    }

    public List<AccurateFoodRecipe> menuAccurateRecipes(ApplianceType cook) {
        State view = state();
        List<AccurateFoodRecipe> out = new ArrayList<>();
        for (AccurateFoodRecipe r : view.menuAccurateRecipes) {
            if (r.cook() == cook) {
                out.add(r);
            }
        }
        return out;
    }

    public List<FlexFoodRecipe> menuFlexRecipes(ApplianceType cook) {
        State view = state();
        List<FlexFoodRecipe> out = new ArrayList<>();
        for (FlexFoodRecipe r : view.menuFlexRecipes) {
            if (r.cook() == cook) {
                out.add(r);
            }
        }
        return out;
    }

    // 按器具取该器具下的全部精确配方 快照 供编辑与浏览 UI 分页
    public List<AccurateFoodRecipe> accurateRecipes(ApplianceType cook) {
        State view = state();
        List<AccurateFoodRecipe> out = new ArrayList<>();
        for (AccurateFoodRecipe r : view.accurateRecipes) {
            if (r.cook() == cook) {
                out.add(r);
            }
        }
        return out;
    }

    public List<FlexFoodRecipe> flexRecipes(ApplianceType cook) {
        State view = state();
        List<FlexFoodRecipe> out = new ArrayList<>();
        for (FlexFoodRecipe r : view.flexRecipes) {
            if (r.cook() == cook) {
                out.add(r);
            }
        }
        return out;
    }

    // UI 编辑走这两个 删除后整表重建索引 registerAccurate 的 putIfAbsent 只认首个
    public boolean removeAccurate(Key id) {
        return update(() -> {
            State view = state();
            if (!view.accurateRecipes.removeIf(r -> r.id().equals(id))) {
                return false;
            }
            rebuildAccurateIndex();
            return true;

        });
    }

    public boolean removeFlex(ApplianceType cook, Key id) {
        return update(() -> {
            State view = state();
            boolean removed = view.flexRecipes.removeIf(r -> r.cook() == cook && r.id().equals(id));
            if (removed) {
                view.carrierRecipes = null;
            }
            return removed;

        });
    }

    public boolean removeChopping(Key id) {
        return update(() -> {
            State view = state();
            return view.choppingRecipes.removeIf(r -> r.id().equals(id));

        });
    }

    public boolean removeTeapot(Key id) {
        return update(() -> {
            State view = state();
            return view.teapotRecipes.removeIf(r -> r.id().equals(id));

        });
    }

    public List<ChoppingBoardRecipe> choppingRecipes() {
        State view = state();
        return List.copyOf(view.choppingRecipes);
    }

    public List<ChoppingBoardRecipe> menuChoppingRecipes() {
        State view = state();
        return List.copyOf(view.menuChoppingRecipes);
    }

    public List<TeapotRecipe> teapotRecipes() {
        State view = state();
        return List.copyOf(view.teapotRecipes);
    }

    public List<TeapotRecipe> menuTeapotRecipes() {
        State view = state();
        return List.copyOf(view.menuTeapotRecipes);
    }

    public void removeMenuAccurate(AccurateFoodRecipe recipe) {
        atomicUpdate(() -> {
            State view = state();
            view.menuAccurateRecipes.removeIf(value -> value == recipe);

        });
    }

    public void removeMenuFlex(FlexFoodRecipe recipe) {
        atomicUpdate(() -> {
            State view = state();
            view.menuFlexRecipes.removeIf(value -> value == recipe);

        });
    }

    public void removeMenuChopping(ChoppingBoardRecipe recipe) {
        atomicUpdate(() -> {
            State view = state();
            view.menuChoppingRecipes.removeIf(value -> value == recipe);

        });
    }

    public void removeMenuTeapot(TeapotRecipe recipe) {
        atomicUpdate(() -> {
            State view = state();
            view.menuTeapotRecipes.removeIf(value -> value == recipe);

        });
    }

    public ChoppingBoardRecipe findChoppingById(Key id) {
        State view = state();
        for (ChoppingBoardRecipe r : view.choppingRecipes) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    public TeapotRecipe findTeapotById(Key id) {
        State view = state();
        for (TeapotRecipe r : view.teapotRecipes) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    private void rebuildAccurateIndex() {
        State view = state();
        view.accurateIndex.clear();
        for (AccurateFoodRecipe r : view.accurateRecipes) {
            view.accurateIndex.putIfAbsent(new AccurateKey(r.cook(), r.input()), r);
        }
    }

    public void registerChopping(ChoppingBoardRecipe r) {
        atomicUpdate(() -> {
            State view = state();
            view.choppingRecipes.add(r);

        });
    }

    public void registerMenuChopping(ChoppingBoardRecipe r) {
        atomicUpdate(() -> {
            State view = state();
            view.menuChoppingRecipes.add(r);

        });
    }

    public void clearFlex(ApplianceType cook) {
        atomicUpdate(() -> {
            State view = state();
            view.flexRecipes.removeIf(r -> r.cook() == cook);
            view.menuFlexRecipes.removeIf(r -> r.cook() == cook);
            view.carrierRecipes = null;
            // Carrier lookup is rebuilt with the same snapshot.

        });
    }

    public void clearAccurate() {
        atomicUpdate(() -> {
            State view = state();
            view.accurateRecipes.clear();
            view.menuAccurateRecipes.clear();
            view.accurateIndex.clear();

        });
    }

    public void clearChopping() {
        atomicUpdate(() -> {
            State view = state();
            view.choppingRecipes.clear();
            view.menuChoppingRecipes.clear();

        });
    }

    public void registerTeapot(TeapotRecipe r) {
        atomicUpdate(() -> {
            State view = state();
            view.teapotRecipes.add(r);

        });
    }

    public void registerMenuTeapot(TeapotRecipe r) {
        atomicUpdate(() -> {
            State view = state();
            view.menuTeapotRecipes.add(r);

        });
    }

    public void clearTeapot() {
        atomicUpdate(() -> {
            State view = state();
            view.teapotRecipes.clear();
            view.menuTeapotRecipes.clear();

        });
    }

    public void registerTeapotLiquid(TeapotLiquid l) {
        atomicUpdate(() -> {
            State view = state();
            view.teapotLiquids.put(l.fluid(), l);
            if (view.defaultLiquid == null) {
                view.defaultLiquid = l;
            }

        });
    }

    public void clearTeapotLiquid() {
        atomicUpdate(() -> {
            State view = state();
            view.teapotLiquids.clear();
            view.defaultLiquid = null;

        });
    }

    public TeapotLiquid getTeapotLiquid(Key fluid) {
        State view = state();
        return view.teapotLiquids.get(fluid);
    }

    public TeapotLiquid getTeapotLiquid(String fluid) {
        return getTeapotLiquid(Key.of(fluid));
    }

    // 已登记的液体 按 id 排序 编辑器列按钮用 顺序不定的话每次开菜单都在跳
    public List<Key> teapotLiquidKeys() {
        State view = state();
        List<Key> out = new ArrayList<>(view.teapotLiquids.keySet());
        out.sort(Comparator.comparing(Key::asString));
        return List.copyOf(out);
    }

    public boolean hasTeapotLiquid(Key fluid) {
        State view = state();
        return view.teapotLiquids.containsKey(fluid);
    }

    public boolean hasTeapotLiquid(String fluid) {
        return hasTeapotLiquid(Key.of(fluid));
    }

    // 空壶液体条用首个注册液体的左右空格字形
    public TeapotLiquid defaultTeapotLiquid() {
        State view = state();
        return view.defaultLiquid;
    }

    public void registerTeaCup(TeaCup c) {
        atomicUpdate(() -> {
            State view = state();
            view.teaCups.put(c.tea(), c);
            view.teaCupsByItem.put(c.item(), c);

        });
    }

    public void clearTeaCup() {
        atomicUpdate(() -> {
            State view = state();
            view.teaCups.clear();
            view.teaCupsByItem.clear();

        });
    }

    public boolean hasTeaCup(Key tea) {
        State view = state();
        return view.teaCups.containsKey(tea);
    }

    public boolean hasTeaCup(String tea) {
        return hasTeaCup(Key.of(tea));
    }

    public TeaCup getTeaCup(Key tea) {
        State view = state();
        return view.teaCups.get(tea);
    }

    public TeaCup getTeaCup(String tea) {
        return getTeaCup(Key.of(tea));
    }

    // 按手持物品 id 找茶杯 用于直接放置茶到杯垫
    public TeaCup getTeaCupByItem(Key itemId) {
        State view = state();
        return view.teaCupsByItem.get(itemId);
    }

    public TeaCup getTeaCupByItem(String itemId) {
        return getTeaCupByItem(Key.of(itemId));
    }

    // 茶杯成品随机取一个展示模型 无则返回 null
    public Key pickTeaModel(Key tea) {
        State view = state();
        TeaCup c = view.teaCups.get(tea);
        if (c == null || c.displayModels().isEmpty()) {
            return null;
        }
        List<Key> models = c.displayModels();
        return models.get(ThreadLocalRandom.current().nextInt(models.size()));
    }

    public Key pickTeaModel(String tea) {
        return pickTeaModel(Key.of(tea));
    }

    // 液体类型与原料共同匹配茶壶配方
    public TeapotRecipe findTeapot(Key fluid, Key input) {
        State view = state();
        if (writer.get() == null) return view.teapotIndex.get(new TeapotKey(fluid, input));
        for (TeapotRecipe r : view.teapotRecipes) {
            if (r.fluid().equals(fluid) && r.input().equals(input)) {
                return r;
            }
        }
        return null;
    }

    public TeapotRecipe findTeapot(String fluid, String input) {
        return findTeapot(Key.of(fluid), Key.of(input));
    }

    public ChoppingBoardRecipe findChoppingByInput(Key input) {
        State view = state();
        if (writer.get() == null) return view.choppingIndex.get(input);
        for (ChoppingBoardRecipe r : view.choppingRecipes) {
            if (r.input().equals(input)) {
                return r;
            }
        }
        return null;
    }

    public ChoppingBoardRecipe findChoppingByInput(String input) {
        return findChoppingByInput(Key.of(input));
    }

    // 按配方模式产出成品 切完调用 返回需要掉落的物品列表 空列表表示无产出
    public List<Item> rollChoppingResults(ChoppingBoardRecipe recipe) {
        List<ChoppingResult> results = recipe.results();
        if (results.isEmpty()) {
            return List.of();
        }
        List<Item> out = new ArrayList<>();
        switch (recipe.mode()) {
            case SINGLE -> addChoppingItem(out, WeightedPicker.pick(results, ChoppingResult::weight));
            case SINGLE_EXTRA -> {
                addChoppingItem(out, WeightedPicker.pick(results, ChoppingResult::weight));
                for (ChoppingResult extra : recipe.extras()) {
                    if (WeightedPicker.roll(extra.weight())) {
                        addChoppingItem(out, extra);
                    }
                }
            }
            case MULTI_RANDOM -> {
                for (ChoppingResult r : results) {
                    if (WeightedPicker.roll(r.weight())) {
                        addChoppingItem(out, r);
                    }
                }
                if (out.isEmpty()) {
                    addChoppingItem(out, WeightedPicker.pick(results, ChoppingResult::weight));
                }
            }
        }
        return out;
    }

    private void addChoppingItem(List<Item> out, ChoppingResult result) {
        Item item = InventoryUtils.createOrEmpty(result.key());
        if (!ItemUtils.isEmpty(item)) {
            out.add(item.copyWithCount(Math.max(1, result.count())));
        }
    }

    public Optional<FoodRecipeResult> findAccurate(ApplianceType type, Key inputItem) {
        State view = state();
        AccurateFoodRecipe recipe = view.accurateIndex.get(new AccurateKey(type, inputItem));
        if (recipe == null) {
            return Optional.empty();
        }
        WeightedResult chosen = WeightedPicker.pick(recipe.results(), WeightedResult::weight);
        if (chosen == null) {
            return Optional.empty();
        }
        Item item = InventoryUtils.createOrEmpty(chosen.key());
        if (ItemUtils.isEmpty(item)) {
            return Optional.empty();
        }
        if (!recipe.lore().isEmpty()) {
            item.loreComponent(recipe.lore().stream()
                    .map(l -> AdventureHelper.miniMessage().deserialize("<!i>" + l))
                    .toList());
        }
        return Optional.of(new FoodRecipeResult(item, recipe.resultCount(), null));
    }

    public Optional<FoodRecipeResult> findAccurate(ApplianceType type, String inputItem) {
        return findAccurate(type, Key.of(inputItem));
    }

    // 石磨研磨该输入所需圈数 配方未指定圈数或无对应配方时用传入的默认值
    public int findGrindRotations(Key inputItem, int defaultRotations) {
        State view = state();
        AccurateFoodRecipe recipe = view.accurateIndex.get(new AccurateKey(ApplianceType.MILLSTONE, inputItem));
        if (recipe == null || recipe.rotations() <= 0) {
            return defaultRotations;
        }
        return recipe.rotations();
    }

    public int findGrindRotations(String inputItem, int defaultRotations) {
        return findGrindRotations(Key.of(inputItem), defaultRotations);
    }

    // 烹饪一道菜 匹配配方里优先选消耗食材最多的 再依次以 unpreferred 种类数 lore 命中数
    // 最早主料位置打破平局 产出 count 份成品 已套用名称与 lore 多余食材丢弃 无匹配返回空
    public Optional<FoodRecipeResult> cookFlex(ApplianceType type, List<Key> ingredientIds) {
        return cookFlex(type, ingredientIds, null);
    }

    // 同上 但按当前汤底桶 id 过滤 配方声明 liquids 时当前汤底须命中其一 炒锅传 null 即可
    public Optional<FoodRecipeResult> cookFlex(ApplianceType type, List<Key> ingredientIds, Key liquid) {
        State view = state();
        FlexMatcher.Match match = match(view, type, ingredientIds, liquid);
        if (match == null) {
            return Optional.empty();
        }
        Item dish = FlexMatcher.buildDish(match);
        if (dish == null) {
            return Optional.empty();
        }
        return Optional.of(new FoodRecipeResult(dish, match.portions(), match.recipe().carrier()));
    }

    public Optional<FlexFoodRecipe> findBestFlexRecipe(ApplianceType type, List<Key> ingredientIds) {
        return findBestFlexRecipe(type, ingredientIds, null);
    }

    // 高汤锅的配方几乎都声明了 liquids 不带汤底查会被整条过滤掉 永远匹配不上
    public Optional<FlexFoodRecipe> findBestFlexRecipe(ApplianceType type, List<Key> ingredientIds, Key liquid) {
        State view = state();
        FlexMatcher.Match match = match(view, type, ingredientIds, liquid);
        return Optional.ofNullable(match == null ? null : match.recipe());
    }

    public AccurateFoodRecipe findAccurateById(Key id) {
        State view = state();
        for (AccurateFoodRecipe r : view.accurateRecipes) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    public AccurateFoodRecipe findAccurateById(String id) {
        return findAccurateById(Key.of(id));
    }

    public FlexFoodRecipe findFlexById(Key id) {
        State view = state();
        for (FlexFoodRecipe r : view.flexRecipes) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    public FlexFoodRecipe findFlexById(String id) {
        return findFlexById(Key.of(id));
    }
}
