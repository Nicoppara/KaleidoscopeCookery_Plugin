import net.kaleidoscope.cookery.block.behavior.ShawarmaSpitBehavior;
import net.kaleidoscope.cookery.block.behavior.StockpotBehavior;
import net.kaleidoscope.cookery.block.entity.ShawarmaSpitController;
import net.kaleidoscope.cookery.block.entity.SteamerController;
import net.kaleidoscope.cookery.block.entity.StockpotController;
import net.kaleidoscope.cookery.block.entity.StockpotStage;
import net.kaleidoscope.cookery.block.entity.TeapotController;
import net.kaleidoscope.cookery.recipe.AccurateFoodRecipe;
import net.kaleidoscope.cookery.recipe.ApplianceFoodRegistry;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.CookingPlan;
import net.kaleidoscope.cookery.recipe.FlexFoodRecipe;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.TeapotRecipe;
import net.kaleidoscope.cookery.recipe.WeightedResult;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.FoliaUtil;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.world.BukkitWorldManager;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dedicated acceptance profile only; exercises real CE controllers on their owner thread.
 * No client or scheduled-tick latency acceptance is claimed. The external runner stops the server.
 */
public final class ProcessingProbe extends JavaPlugin {
    private static final Key FAST = Key.of("minecraft:nether_star");
    private static final Key SLOW = Key.of("minecraft:echo_shard");
    private static final Key BREAD = Key.of("minecraft:bread");
    private static final Key CARROT = Key.of("minecraft:carrot");
    private static final Key WATER = Key.of("minecraft:water");
    private static final Key WATER_BUCKET = Key.of("minecraft:water_bucket");
    private final List<String> checks = new ArrayList<>();
    private final List<Key> temporaryRecipes = new ArrayList<>();
    private final Map<BlockPos, BlockData> physicalBefore = new LinkedHashMap<>();
    private final Map<ApplianceType, List<Key>> addedFoods = new LinkedHashMap<>();
    private World world;
    private CEWorld ceWorld;
    private boolean forceLoadedBefore;
    private boolean ticketPrepared;
    private int stockpotX;
    private Map<String, Object> performance = Map.of();
    private final Player creativePlayer = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, args) -> {
                if (method.getName().equals("canInstabuild")) return true;
                if (method.getName().equals("toString")) return "ProcessingProbeCreativePlayer";
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                throw new UnsupportedOperationException("Unexpected simulated player call: " + method.getName());
            });

    @Override public void onEnable() {
        String expectedWorld = System.getProperty("cookery.processing.world", "processing-verification");
        if (!Boolean.getBoolean("cookery.processing.acceptance") || expectedWorld.isBlank() || expectedWorld.equals("world")) {
            writeReport(new IllegalStateException("Explicit isolated acceptance world and flag required"));
            return;
        }
        World target = Bukkit.getWorld(expectedWorld);
        if (target == null) {
            writeReport(new IllegalStateException("Acceptance world is unavailable: " + expectedWorld));
            return;
        }
        world = target;
        net.momirealms.craftengine.core.plugin.CraftEngine.instance().scheduler().platform().run(() -> {
            try {
                if (!Bukkit.getOnlinePlayers().isEmpty()) throw new IllegalStateException("Acceptance requires no online players");
                forceLoadedBefore = target.isChunkForceLoaded(0, 0);
                target.setChunkForceLoaded(0, 0, true);
                ticketPrepared = true;
                FoliaUtil.runLater(this::runProbe, 100L, new Location(target, 8, 100, 8));
            } catch (Throwable error) { finishReport(error); }
        });
    }

    private void runProbe() {
        Throwable failure = null;
        try {
            check(Boolean.getBoolean("cookery.processing.acceptance"), "dedicated acceptance flag");
            String expectedWorld = System.getProperty("cookery.processing.world", "processing-verification");
            check(!expectedWorld.equals("world") && !expectedWorld.isBlank(), "explicit isolated world name");
            world = Bukkit.getWorld(expectedWorld);
            check(world != null && Bukkit.getOnlinePlayers().isEmpty(), "isolated world exists and no players are online");
            check(ownerThread(), "all controller operations use the owner thread");
            String expectedCe = System.getProperty("cookery.processing.craftengine");
            check(expectedCe != null && expectedCe.equals(craftEngineVersion()), "exact CraftEngine dependency version");
            check(Bukkit.getPluginManager().isPluginEnabled("KaleidoscopeCookeryPlugin"), "cookery plugin enabled");
            world.getChunkAt(0, 0).load();
            ceWorld = BukkitWorldManager.instance().getWorld(world.getUID()).storageWorld();
            stockpotX = 8 + (int) Math.floorMod(-world.getGameTime() - 8 * 31L - 8, 5);
            for (int x = 8; x <= 12; x++) {
                rememberAndSet(x, 99, 8, Material.MAGMA_BLOCK);
                rememberAndSet(x, 101, 8, Material.AIR);
            }
            if (!Boolean.getBoolean("cookery.processing.performance-only")) {
                steamerCases();
                shawarmaCases();
                stockpotCases();
                teapotCases();
                InteractiveProcessingProbe.verify(this, world, checks);
            }
            performance = ProcessingPerformanceProbe.measure(this, world, checks);
            getLogger().info("PROCESSING_PROBE_PASS " + checks.size() + " checks");
        } catch (Throwable error) {
            failure = error;
            getLogger().log(java.util.logging.Level.SEVERE, "PROCESSING_PROBE_FAIL", error);
        } finally {
            try { cleanup(); }
            catch (Throwable error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
            finishReport(failure);
        }
    }

    private void steamerCases() throws Exception {
        FoodRecipeRegistry.instance().atomicUpdate(() -> {
            accurate(ApplianceType.STEAMER, "steamer_fast", FAST, BREAD, 3);
            accurate(ApplianceType.STEAMER, "steamer_slow", SLOW, BREAD, 7);
            allow(ApplianceType.STEAMER, FAST);
            allow(ApplianceType.STEAMER, SLOW);
        });
        SteamerController slots = steamer();
        check(slots.tryAddOne(item(FAST)) && slots.tryAddOne(item(SLOW)), "steamer accepts two recipe inputs");
        check(slots.getCookingTime()[0] == 3 && slots.getCookingTime()[1] == 7, "steamer resolves time separately for each slot");
        call(slots, "cookingTick", true);
        CompoundTag saved = save(slots);
        call(slots, "cookingTick", true);
        slots.takeFood(null);
        CompoundTag snapshotData = saved.getCompound(SteamerController.DATA_KEY);
        check(snapshotData.getIntArray("cooking_progress")[0] == 1
                && snapshotData.getIntArray("cooking_progress")[1] == 1
                && snapshotData.getIntArray("cooking_time")[0] == 3
                && snapshotData.getIntArray("cooking_time")[1] == 7,
                "steamer saved snapshot arrays remain independent while live slots advance and compact");
        check(CookingPlan.load(saved.getCompound(SteamerController.DATA_KEY).getCompound("cooking_plans")
                .getCompound("0")).recipeId().equals(id("steamer_fast")), "steamer NBT saves frozen recipe identity");
        SteamerController restored = steamer();
        restored.loadCustomData(saved);
        check(saved.equals(save(restored)), "steamer NBT preserves plan, progress and duration");
        accurate(ApplianceType.STEAMER, "steamer_fast", FAST, CARROT, 1);
        call(restored, "cookingTick", true);
        check(restored.getCookingTime()[0] == 3 && restored.getCookingProgress()[0] == 2,
                "steamer running slot keeps duration after recipe replacement");
        call(restored, "cookingTick", true);
        check(restored.getItems()[0].id().equals(BREAD) && restored.getCookingTime()[0] == -1,
                "steamer running slot keeps original result after recipe replacement");
        check(restored.getCookingTime()[1] == 7 && restored.getCookingProgress()[1] == 3,
                "steamer longer slot remains independent");
        SteamerController next = steamer();
        check(next.tryAddOne(item(FAST)) && next.getCookingTime()[0] == 1, "steamer next batch uses new recipe time");
        call(next, "cookingTick", true);
        check(next.getItems()[0].id().equals(CARROT), "steamer next batch uses new recipe result");
        SteamerController moving = steamer();
        moving.tryAddOne(item(FAST));
        moving.tryAddOne(item(SLOW));
        check(moving.takeFood(null).id().equals(FAST), "steamer raw removal returns input");
        check(moving.getCookingTime()[0] == 7 && CookingPlan.load(save(moving).getCompound(SteamerController.DATA_KEY)
                .getCompound("cooking_plans").getCompound("0")).recipeId().equals(id("steamer_slow")),
                "steamer compaction moves duration and plan with the food");
        SteamerController cooling = steamer();
        cooling.tryAddOne(item(SLOW));
        for (int i = 0; i < 4; i++) call(cooling, "cookingTick", true);
        rememberAndSet(8, 99, 8, Material.AIR);
        cooling.tick();
        check(cooling.getCookingProgress()[0] == 2, "steamer retains existing two-tick cooling loss");
        rememberAndSet(8, 99, 8, Material.MAGMA_BLOCK);
        SteamerController noLid = steamer();
        noLid.setHasLid(false);
        noLid.tryAddOne(item(SLOW));
        noLid.tick();
        check(noLid.getCookingProgress()[0] == 0, "steamer with heat but without cover pauses");
        CompoundTag legacy = legacySteamer(item(SLOW), 9, 4);
        SteamerController old = steamer();
        old.loadCustomData(legacy);
        call(old, "hydrateLegacyPlans");
        check(old.getCookingTime()[0] == 9 && old.getCookingProgress()[0] == 4,
                "legacy steamer preserves saved total and progress");
        check(CookingPlan.load(save(old).getCompound(SteamerController.DATA_KEY).getCompound("cooking_plans")
                .getCompound("0")).workRequired() == 9, "legacy steamer hydrates plan without resetting duration");
        CompoundTag corrupt = save(old);
        corrupt.getCompound(SteamerController.DATA_KEY).getCompound("cooking_plans").getCompound("0").putInt("version", 999);
        SteamerController blocked = steamer();
        blocked.loadCustomData(corrupt);
        call(blocked, "cookingTick", true);
        check(blocked.getItems()[0].id().equals(SLOW) && save(blocked).getCompound(SteamerController.DATA_KEY)
                .getIntArray("completion_blocked")[0] == 1, "corrupt steamer plan stops and retains food");
        FoodRecipeRegistry.instance().atomicUpdate(() -> {
            accurate(ApplianceType.STEAMER, "steamer_dirty", SLOW, BREAD, 100);
            FoodRecipeRegistry.instance().removeAccurate(id("steamer_slow"));
        });
        SteamerController dirty = steamer();
        dirty.tick();
        dirty.tryAddOne(item(SLOW));
        var chunk = ceWorld.getChunkAtIfLoaded(0, 0);
        chunk.setUnsaved(false);
        for (int i = 0; i < 20; i++) dirty.tick();
        check(chunk.isUnsaved(), "active steamer marks its chunk dirty within twenty ticks");
    }

    private void shawarmaCases() throws Exception {
        FoodRecipeRegistry.instance().atomicUpdate(() -> {
            accurate(ApplianceType.SHAWARMA, "shawarma_fast", FAST, BREAD, 3);
            accurate(ApplianceType.SHAWARMA, "shawarma_slow", SLOW, BREAD, 7);
            allow(ApplianceType.SHAWARMA, FAST);
            allow(ApplianceType.SHAWARMA, SLOW);
        });
        ShawarmaSpitController grill = shawarma(true);
        check(grill.tryAddOne(0, item(FAST)) && grill.tryAddOne(1, item(SLOW)), "shawarma accepts separate layer inputs");
        grill.tick();
        CompoundTag saved = save(grill);
        check(planEntries(saved, "kaleidoscopecookery:shawarma_spit").size() == 2,
                "shawarma persists one frozen plan per occupied slot");
        ShawarmaSpitController restored = shawarma(true);
        restored.loadCustomData(saved);
        check(saved.equals(save(restored)), "shawarma NBT round trip preserves plans and progress");
        accurate(ApplianceType.SHAWARMA, "shawarma_fast", FAST, CARROT, 1);
        powered(restored, false);
        CompoundTag beforePause = save(restored);
        for (int i = 0; i < 8; i++) restored.tick();
        check(beforePause.equals(save(restored)), "shawarma redstone off pauses all slot progress");
        powered(restored, true);
        restored.tick();
        restored.tick();
        check(restored.getItems()[0][0].id().equals(BREAD) && restored.getItems()[1][0].id().equals(SLOW),
                "shawarma frozen fast result completes while slow layer continues");
        ShawarmaSpitController next = shawarma(true);
        next.tryAddOne(0, item(FAST));
        next.tick();
        check(next.getItems()[0][0].id().equals(CARROT), "shawarma next batch gets changed one-tick recipe");
        CompoundTag oldEntry = new CompoundTag();
        oldEntry.putInt("layer", 1);
        oldEntry.putInt("slot", 3);
        oldEntry.put("item", BlockEntityNbt.itemTag(item(SLOW)));
        oldEntry.putInt("progress", 4);
        oldEntry.putInt("time", 9);
        var entries = new net.momirealms.craftengine.libraries.nbt.ListTag();
        entries.add(oldEntry);
        CompoundTag oldData = new CompoundTag();
        oldData.putInt("data_version", VersionHelper.WORLD_VERSION);
        oldData.put("items", entries);
        CompoundTag legacy = new CompoundTag();
        legacy.put("kaleidoscopecookery:shawarma_spit", oldData);
        ShawarmaSpitController old = shawarma(true);
        old.loadCustomData(legacy);
        call(old, "hydrateLegacyPlans");
        CompoundTag persisted = (CompoundTag) planEntries(save(old), "kaleidoscopecookery:shawarma_spit").get(0);
        check(persisted.getInt("progress", 0) == 4 && persisted.getInt("time", 0) == 9
                && CookingPlan.load(persisted.getCompound("cooking_plan")).workRequired() == 9,
                "legacy shawarma preserves per-layer duration and progress");
    }

    private void stockpotCases() throws Exception {
        flex("stockpot", BREAD, 5);
        StockpotController pot = stockpot();
        check(pot.addSoupBase(WATER_BUCKET, true) && pot.addIngredient(item(FAST)), "stockpot accepts soup base and input");
        lid(pot, true);
        pot.tick();
        check(pot.stage() == StockpotStage.COOKING && pot.currentTick() == 5, "stockpot starts using matched recipe duration");
        pot.tick();
        CompoundTag saved = save(pot);
        check(saved.getCompound("kaleidoscopecookery:stockpot").getCompound("cooking_plan") != null,
                "stockpot persists frozen batch plan");
        flex("stockpot", CARROT, 1);
        StockpotController restored = stockpot();
        lid(restored, true);
        restored.loadCustomData(saved);
        check(restored.currentTick() == 4, "stockpot load and recipe replacement preserve remaining time");
        lid(restored, false);
        restored.tick();
        check(restored.currentTick() == 4, "stockpot lid removed pauses remaining time");
        lid(restored, true);
        rememberAndSet(stockpotX, 99, 8, Material.AIR);
        set(restored, "heatCacheInitialized", false);
        restored.tick();
        check(restored.currentTick() == 4, "stockpot heat removed pauses remaining time");
        rememberAndSet(stockpotX, 99, 8, Material.MAGMA_BLOCK);
        set(restored, "heatCacheInitialized", false);
        for (int i = 0; i < 4; i++) restored.tick();
        lid(restored, false);
        check(restored.peekResult().id().equals(BREAD), "stockpot finishes original planned dish after recipe replacement");
        StockpotController next = stockpot();
        next.addSoupBase(WATER_BUCKET, true);
        next.addIngredient(item(FAST));
        lid(next, true);
        next.tick();
        check(next.currentTick() == 1, "stockpot next batch resolves new duration");
        next.tick();
        lid(next, false);
        check(next.peekResult().id().equals(CARROT), "stockpot next batch resolves new output");
        StockpotController changed = stockpot();
        changed.addSoupBase(WATER_BUCKET, true);
        changed.addIngredient(item(FAST));
        lid(changed, true);
        changed.tick();
        lid(changed, false);
        check(changed.addIngredient(item(SLOW)) && changed.stage() == StockpotStage.PUT_INGREDIENT
                && changed.currentTick() == -1, "stockpot ingredient change resets the cooking batch");
        check(changed.extractIngredient(null).id().equals(SLOW), "stockpot changed ingredient can be reclaimed");
        CompoundTag oldData = new CompoundTag();
        oldData.putInt("data_version", VersionHelper.WORLD_VERSION);
        oldData.putInt("status", StockpotStage.COOKING.ordinal());
        oldData.putInt("current_tick", 9);
        oldData.putString("soup_base_id", WATER_BUCKET.asString());
        oldData.put("ingredients", BlockEntityNbt.saveItems(List.of(item(FAST))));
        CompoundTag legacy = new CompoundTag();
        legacy.put("kaleidoscopecookery:stockpot", oldData);
        StockpotController old = stockpot();
        old.loadCustomData(legacy);
        lid(old, false);
        old.tick();
        check(old.currentTick() == 9 && save(old).getCompound("kaleidoscopecookery:stockpot")
                .getCompound("cooking_plan") != null, "legacy stockpot hydrates plan without replacing its saved remaining time");
    }

    private void teapotCases() throws Exception {
        for (int time : new int[]{1, 2, 22, 23, 24}) {
            tea("tea", BREAD, time);
            TeapotController tea = teapot();
            set(tea, "fluid", WATER);
            check(tea.addIngredient(creativePlayer, item(FAST)), "tea " + time + " input accepted");
            for (int i = 0; i < 199; i++) call(tea, "tick");
            check(tea.getStatus() == TeapotController.PUT_INGREDIENT, "tea " + time + " retains 200-tick preparation stage");
            call(tea, "tick");
            check(tea.getStatus() == TeapotController.PROCESSING && remaining(tea) == time,
                    "tea " + time + " starts exact recipe processing duration");
            for (int i = 0; i < time - 1; i++) call(tea, "tick");
            check(tea.getStatus() == TeapotController.PROCESSING, "tea " + time + " does not finish early");
            call(tea, "tick");
            check(tea.getStatus() == TeapotController.FINISHED && result(tea).id().equals(BREAD),
                    "tea " + time + " finishes on the configured tick without 23-tick quantization");
        }
        tea("tea", BREAD, 24);
        TeapotController frozen = teapot();
        set(frozen, "fluid", WATER);
        frozen.addIngredient(creativePlayer, item(FAST));
        for (int i = 0; i < 210; i++) call(frozen, "tick");
        CompoundTag saved = save(frozen);
        check(remaining(frozen) == 14, "tea exact processing progress is serializable");
        tea("tea", CARROT, 1);
        TeapotController restored = teapot();
        restored.loadCustomData(saved);
        check(saved.equals(save(restored)), "tea NBT retains frozen plan and exact remaining time");
        rememberAndSet(8, 99, 8, Material.AIR);
        set(restored, "heatCacheInitialized", false);
        for (int i = 0; i < 8; i++) call(restored, "tick");
        check(remaining(restored) == 14, "tea lost heat pauses exact remaining time");
        rememberAndSet(8, 99, 8, Material.MAGMA_BLOCK);
        set(restored, "heatCacheInitialized", false);
        for (int i = 0; i < 14; i++) call(restored, "tick");
        check(result(restored).id().equals(BREAD), "tea active batch keeps original output after recipe replacement");
        TeapotController next = teapot();
        set(next, "fluid", WATER);
        next.addIngredient(creativePlayer, item(FAST));
        for (int i = 0; i < 201; i++) call(next, "tick");
        check(next.getStatus() == TeapotController.FINISHED && result(next).id().equals(CARROT),
                "tea next batch uses new processing duration and result");
        CompoundTag oldData = new CompoundTag();
        oldData.putInt("status", TeapotController.PROCESSING);
        oldData.putInt("current_tick", 5);
        oldData.putInt("servings", 4);
        oldData.putString("fluid", WATER.asString());
        BlockEntityNbt.putItem(oldData, "input", item(FAST));
        BlockEntityNbt.putItem(oldData, "result", item(BREAD));
        CompoundTag legacy = new CompoundTag();
        legacy.put("kaleidoscopecookery:teapot", oldData);
        TeapotController old = teapot();
        old.loadCustomData(legacy);
        for (int i = 0; i < 5; i++) call(old, "tick");
        check(old.getStatus() == TeapotController.FINISHED && result(old).id().equals(BREAD),
                "legacy processing tea preserves its already-built result without reroll");
        FoodRecipeRegistry.instance().atomicUpdate(() -> {
            tea("tea_missing", Key.of("cookery_processing_probe:missing_result"), 1);
            FoodRecipeRegistry.instance().removeTeapot(id("tea"));
        });
        TeapotController missing = teapot();
        set(missing, "fluid", WATER);
        missing.addIngredient(creativePlayer, item(FAST));
        for (int i = 0; i < 201; i++) call(missing, "tick");
        CompoundTag stopped = save(missing).getCompound("kaleidoscopecookery:teapot");
        check(stopped.getBoolean("completion_blocked", false) && !((Item) get(missing, "input")).isEmpty()
                && missing.getStatus() == TeapotController.PROCESSING,
                "tea unavailable planned result blocks completion and preserves input");
    }

    private SteamerController steamer() {
        SteamerController controller = appliance("steamer", SteamerController.class, 8).controller();
        controller.setHasLid(true);
        return controller;
    }
    private ShawarmaSpitController shawarma(boolean powered) {
        ShawarmaSpitController controller = appliance("shawarma_spit", ShawarmaSpitController.class, 8).controller();
        powered(controller, powered);
        return controller;
    }
    private StockpotController stockpot() { return appliance("stockpot", StockpotController.class, stockpotX).controller(); }
    private TeapotController teapot() { return appliance("teapot", TeapotController.class, 8).controller(); }
    private record Appliance<T extends BlockEntityController>(BlockEntity entity, T controller) {}
    private <T extends BlockEntityController> Appliance<T> appliance(String block, Class<T> type, int x) {
        var definition = CraftEngineBlocks.byId(Key.of("kaleidoscopecookery:" + block));
        if (definition == null) throw new AssertionError("Missing real pack block " + block);
        BlockEntity entity = new BlockEntity(new BlockPos(x, 100, 8), definition.defaultState());
        entity.setWorld(ceWorld);
        T controller = entity.controller.getAt(type, 0);
        if (controller == null) throw new AssertionError("Missing real controller " + type);
        return new Appliance<>(entity, controller);
    }
    private void lid(StockpotController pot, boolean lid) {
        BlockEntity entity = pot.blockEntity();
        StockpotBehavior behavior = entity.blockState.behavior().getFirst(StockpotBehavior.class);
        entity.blockState = BlockStates.with(entity.blockState, behavior.getHasLidProperty(), lid);
    }
    private void powered(ShawarmaSpitController grill, boolean powered) {
        BlockEntity entity = grill.blockEntity();
        ShawarmaSpitBehavior behavior = entity.blockState.behavior().getFirst(ShawarmaSpitBehavior.class);
        entity.blockState = BlockStates.with(entity.blockState, behavior.getPoweredProperty(), powered);
    }
    private void accurate(ApplianceType type, String name, Key input, Key output, int time) {
        Key id = id(name);
        FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
        registry.atomicUpdate(() -> {
            registry.removeAccurate(id);
            registry.registerAccurate(new AccurateFoodRecipe(id, input, List.of(new WeightedResult(output, 1)),
                    type, 0, 1, List.of(), time));
        });
        temporaryRecipes.add(id);
    }
    private void flex(String name, Key result, int time) {
        Key id = id(name);
        FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
        registry.atomicUpdate(() -> {
            registry.removeFlex(ApplianceType.STOCKPOT, id);
            registry.registerFlex(FlexFoodRecipe.of(id, result, ApplianceType.STOCKPOT, Map.of(FAST, 1),
                    List.of(WATER_BUCKET), null, false, false, time, 0));
        });
        temporaryRecipes.add(id);
    }
    private void tea(String name, Key result, int time) {
        Key id = id(name);
        FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
        registry.atomicUpdate(() -> {
            registry.removeTeapot(id);
            registry.registerTeapot(new TeapotRecipe(id, WATER, FAST, 1, result, 1, time));
        });
        temporaryRecipes.add(id);
    }
    private void allow(ApplianceType type, Key input) {
        ApplianceFoodRegistry foods = ApplianceFoodRegistry.instance();
        if (!foods.isAllowed(type, input)) {
            addedFoods.computeIfAbsent(type, ignored -> new ArrayList<>()).add(input);
            foods.register(type, input);
        }
    }
    private CompoundTag legacySteamer(Item item, int time, int progress) {
        CompoundTag data = new CompoundTag();
        data.putInt("data_version", VersionHelper.WORLD_VERSION);
        data.putBoolean("has_lid", true);
        data.put("items", BlockEntityNbt.saveItems(new Item[]{item}, 1));
        data.putIntArray("cooking_time", new int[]{time, 0, 0, 0, 0, 0, 0, 0});
        data.putIntArray("cooking_progress", new int[]{progress, 0, 0, 0, 0, 0, 0, 0});
        CompoundTag saved = new CompoundTag();
        saved.put(SteamerController.DATA_KEY, data);
        return saved;
    }
    private net.momirealms.craftengine.libraries.nbt.ListTag planEntries(CompoundTag saved, String key) {
        return saved.getCompound(key).getList("items");
    }
    private static Key id(String name) { return Key.of("cookery_processing_probe:" + name); }
    private static Item item(Key key) { return InventoryUtils.createOrEmpty(key); }
    private static CompoundTag save(BlockEntityController controller) {
        CompoundTag saved = new CompoundTag();
        controller.saveCustomData(saved);
        return saved;
    }
    private static int remaining(TeapotController controller) throws Exception { return (int) get(controller, "currentTick"); }
    private static Item result(TeapotController controller) throws Exception { return (Item) get(controller, "result"); }
    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private static Object call(Object target, String name, Object... args) throws Exception {
        Class<?>[] parameters = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) parameters[i] = args[i] instanceof Boolean ? boolean.class : args[i].getClass();
        Method method = target.getClass().getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        try { return method.invoke(target, args); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception cause) throw cause;
            if (error.getCause() instanceof Error cause) throw cause;
            throw error;
        }
    }
    private void rememberAndSet(int x, int y, int z, Material material) {
        BlockPos pos = new BlockPos(x, y, z);
        physicalBefore.computeIfAbsent(pos, ignored -> world.getBlockAt(x, y, z).getBlockData().clone());
        world.getBlockAt(x, y, z).setType(material, false);
    }
    private void cleanup() {
        FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
        if (!temporaryRecipes.isEmpty() || !addedFoods.isEmpty()) {
            registry.atomicUpdate(() -> {
                for (Key id : temporaryRecipes) {
                    registry.removeAccurate(id);
                    registry.removeFlex(ApplianceType.STOCKPOT, id);
                    registry.removeTeapot(id);
                }
                addedFoods.forEach((type, inputs) -> inputs.forEach(input -> ApplianceFoodRegistry.instance().unregister(type, input)));
            });
        }
        if (world != null) {
            physicalBefore.forEach((pos, before) -> world.getBlockAt(pos.x(), pos.y(), pos.z()).setBlockData(before, false));
        }
    }
    private void finishReport(Throwable failure) {
        net.momirealms.craftengine.core.plugin.CraftEngine.instance().scheduler().platform().run(() -> {
            Throwable finalFailure = failure;
            try {
                if (ticketPrepared) {
                    world.setChunkForceLoaded(0, 0, forceLoadedBefore);
                    ticketPrepared = false;
                }
            } catch (Throwable restoreFailure) {
                if (finalFailure == null) finalFailure = restoreFailure; else finalFailure.addSuppressed(restoreFailure);
            }
            writeReport(finalFailure);
        });
    }
    private String craftEngineVersion() {
        var plugin = Bukkit.getPluginManager().getPlugin("CraftEngine");
        return plugin == null ? "missing" : plugin.getDescription().getVersion();
    }
    private boolean ownerThread() throws Exception {
        if (!FoliaUtil.isFolia()) return Bukkit.isPrimaryThread();
        Method owns = Bukkit.class.getMethod("isOwnedByCurrentRegion", Location.class);
        return (boolean) owns.invoke(null, new Location(world, 8, 100, 8));
    }
    private void check(boolean success, String name) {
        if (!success) throw new AssertionError(name);
        checks.add(name);
    }
    private void writeReport(Throwable failure) {
        StringBuilder json = new StringBuilder("{\n  \"passed\": ").append(failure == null)
                .append(",\n  \"craftengine\": ").append(quote(craftEngineVersion()))
                .append(",\n  \"server\": ").append(quote(Bukkit.getVersion()))
                .append(",\n  \"world\": ").append(quote(world == null ? "none" : world.getName()))
                .append(",\n  \"scope\": ").append(quote("Real CE pack and controllers; owner-thread direct tick simulation; graphical client and scheduled real-time pacing are outside this report"))
                .append(",\n  \"folia\": ").append(FoliaUtil.isFolia())
                .append(",\n  \"performance_only\": ").append(Boolean.getBoolean("cookery.processing.performance-only"))
                .append(",\n  \"check_count\": ").append(checks.size()).append(",\n  \"checks\": [");
        for (int i = 0; i < checks.size(); i++) {
            if (i > 0) json.append(',');
            json.append("\n    ").append(quote(checks.get(i)));
        }
        json.append("\n  ],\n  \"performance\": ").append(jsonValue(performance))
                .append(",\n  \"error\": ").append(failure == null ? "null" : quote(failure.toString())).append("\n}\n");
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(getDataFolder().toPath().resolve("processing-result.json"), json, StandardCharsets.UTF_8);
        } catch (Exception error) {
            getLogger().log(java.util.logging.Level.SEVERE, "Cannot write processing verification report", error);
        }
    }
    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + '"';
    }
    private static String jsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder result = new StringBuilder("{");
            boolean separator = false;
            for (var entry : map.entrySet()) {
                if (separator) result.append(',');
                separator = true;
                result.append(quote(entry.getKey().toString())).append(':').append(jsonValue(entry.getValue()));
            }
            return result.append('}').toString();
        }
        if (value instanceof Iterable<?> entries) {
            StringBuilder result = new StringBuilder("[");
            boolean separator = false;
            for (Object entry : entries) {
                if (separator) result.append(',');
                separator = true;
                result.append(jsonValue(entry));
            }
            return result.append(']').toString();
        }
        return quote(value.toString());
    }
}
