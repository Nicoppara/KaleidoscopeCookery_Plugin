import net.kaleidoscope.cookery.api.event.MillstoneGrindCompleteEvent;
import net.kaleidoscope.cookery.api.event.PotStirFryEvent;
import net.kaleidoscope.cookery.block.entity.ChoppingBoardController;
import net.kaleidoscope.cookery.block.entity.MillstoneController;
import net.kaleidoscope.cookery.block.entity.PotController;
import net.kaleidoscope.cookery.block.entity.PotElement;
import net.kaleidoscope.cookery.block.entity.PotStage;
import net.kaleidoscope.cookery.recipe.*;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import net.momirealms.craftengine.bukkit.world.BukkitWorldManager;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/** Owner-thread calls against real pack controllers. No graphical client or Folia acceptance is implied. */
public final class InteractiveProcessingProbe {
    private static final Key INPUT = Key.of("minecraft:echo_shard");
    private static final Key BREAD = Key.of("minecraft:bread");
    private static final Key CARROT = Key.of("minecraft:carrot");
    private static final Key MISSING = Key.of("interactive_processing_probe:missing_output");
    private final JavaPlugin plugin;
    private final World world;
    private final CEWorld ceWorld;
    private final List<String> checks;
    private final Map<BlockPos, BlockData> before = new LinkedHashMap<>();
    private final Set<UUID> originalEntities;
    private final Map<ApplianceType, Boolean> originalAllowed = new EnumMap<>(ApplianceType.class);
    private final List<BukkitFurniture> furniture = new ArrayList<>();
    private final List<BlockEntityController> controllers = new ArrayList<>();
    private final Cancellation listener = new Cancellation();
    private final Player actor;

    private static final class Cancellation implements Listener {
        boolean cancelStir;
        boolean cancelGrind;
        int stirEvents;
        int grindEvents;
        @EventHandler public void stir(PotStirFryEvent event) {
            stirEvents++;
            if (cancelStir) event.setCancelled(true);
        }
        @EventHandler public void grind(MillstoneGrindCompleteEvent event) {
            grindEvents++;
            if (cancelGrind) event.setCancelled(true);
        }
    }

    private InteractiveProcessingProbe(JavaPlugin plugin, World world, List<String> checks) {
        this.plugin = plugin;
        this.world = world;
        this.checks = checks;
        this.ceWorld = BukkitWorldManager.instance().getWorld(world.getUID()).storageWorld();
        this.originalEntities = new HashSet<>();
        world.getEntities().forEach(entity -> originalEntities.add(entity.getUniqueId()));
        UUID id = UUID.nameUUIDFromBytes("InteractiveProcessingProbe".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        org.bukkit.entity.Player platform = (org.bukkit.entity.Player) Proxy.newProxyInstance(
                org.bukkit.entity.Player.class.getClassLoader(), new Class<?>[]{org.bukkit.entity.Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> "InteractiveProcessingProbe";
                    case "isOnline", "isValid" -> true;
                    case "getWorld" -> world;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> id.hashCode();
                    case "toString" -> "InteractiveProcessingProbePlatformPlayer";
                    default -> throw new UnsupportedOperationException("Unexpected platform call " + method.getName());
                });
        actor = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "platformPlayer" -> platform;
                    case "sendActionBar", "sendPacket", "swingHand", "giveItem" -> null;
                    case "canInstabuild" -> true;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> id.hashCode();
                    case "toString" -> "InteractiveProcessingProbeActor";
                    default -> throw new UnsupportedOperationException("Unexpected actor call " + method.getName());
                });
    }

    public static void verify(JavaPlugin plugin, World world, List<String> checks) throws Exception {
        if (!Bukkit.isOwnedByCurrentRegion(world, 0, 0)) throw new AssertionError("Interactive fixture must run on its chunk owner");
        InteractiveProcessingProbe probe = new InteractiveProcessingProbe(plugin, world, checks);
        Bukkit.getPluginManager().registerEvents(probe.listener, plugin);
        try {
            if (CraftEngineBlocks.isCustomBlock(world.getBlockAt(8, 100, 12)))
                throw new AssertionError("Reserved interactive fixture cell already contains a custom block");
            probe.remember(8, 100, 12, Material.AIR);
            probe.remember(8, 99, 12, Material.MAGMA_BLOCK);
            probe.remember(8, 101, 12, Material.AIR);
            for (int x = 10; x <= 14; x++) for (int z = 10; z <= 14; z++) probe.remember(x, 99, z, Material.STONE);
            for (ApplianceType type : List.of(ApplianceType.POT, ApplianceType.CHOPPING_BOARD, ApplianceType.MILLSTONE)) {
                probe.originalAllowed.put(type, ApplianceFoodRegistry.instance().isAllowed(type, INPUT));
                ApplianceFoodRegistry.instance().register(type, INPUT);
            }
            probe.potCases();
            probe.choppingCases();
            probe.millstoneCases();
        } finally { probe.cleanup(); }
    }

    private void potCases() throws Exception {
        flex(BREAD, 2);
        PotController pot = appliance("pot", PotController.class);
        check(pot.addIngredient(item(INPUT), true, null), "pot accepts registered input");
        pot.setHasOil(true);
        listener.cancelStir = true;
        check(pot.stirFry(true, actor) == PotController.StirResult.IDLE, "cancelled pot event rejects stir");
        check(data(pot, "cooking_pot").getInt("stir_fry_count", 0) == 0, "cancelled stir preserves count and ingredients");
        listener.cancelStir = false;
        check(pot.stirFry(true, actor) == PotController.StirResult.OK, "pot first stir accepted");
        check(plan(pot, "cooking_pot").workRequired() == 2, "pot freezes recipe-specific stir count");
        check(pot.stirFry(true, actor) == PotController.StirResult.IDLE, "pot refuses overlapping animation");
        ((PotElement) get(pot, "element")).hide(actor);
        for (int i = 0; i < 10; i++) pot.tick();
        CompoundTag saved = save(pot);
        check(saved.getCompound("kaleidoscopecookery:cooking_pot").getInt("stir_animation_ticks", -1) == 14,
                "pot saves remaining 24-tick logical animation independently of hide");
        flex(CARROT, 1);
        PotController restored = appliance("pot", PotController.class);
        restored.loadCustomData(saved);
        check(saved.equals(save(restored)), "pot NBT round trip preserves plan, count and animation");
        for (int i = 0; i < 14; i++) restored.tick();
        check(restored.stage() == PotStage.COOKING && plan(restored, "cooking_pot").workRequired() == 2,
                "pot restored first stir keeps frozen count after recipe edit");
        check(restored.stirFry(true, null) == PotController.StirResult.OK, "pot second stir accepted after restored animation");
        for (int i = 0; i < 23; i++) restored.tick();
        check(restored.stage() == PotStage.COOKING && restored.resultCount() == 0, "pot waits until logical tick 24");
        restored.tick();
        check(restored.stage() == PotStage.DONE && restored.peekResult().id().equals(BREAD),
                "pot tick 24 commits original frozen result despite recipe edit");
        int serveWindow = ((net.kaleidoscope.cookery.block.behavior.PotBehavior) get(restored, "behavior")).cookDoneTime;
        check(restored.currentTick() == serveWindow, "pot independent serving/burn window remains unchanged");
        PotController next = appliance("pot", PotController.class);
        next.addIngredient(item(INPUT), true, null); next.setHasOil(true); next.stirFry(true, null);
        for (int i = 0; i < 24; i++) next.tick();
        check(next.stage() == PotStage.DONE && next.peekResult().id().equals(CARROT), "next pot batch uses changed count and output");
        PotController changed = appliance("pot", PotController.class);
        flex(BREAD, 3);
        changed.addIngredient(item(INPUT), true, null); changed.setHasOil(true); changed.stirFry(true, null);
        for (int i = 0; i < 24; i++) changed.tick();
        check(changed.extractItem(null).id().equals(INPUT), "partly stirred pot input can be recovered");
        check(data(changed, "cooking_pot").getInt("stir_fry_count", -1) == 0
                && !data(changed, "cooking_pot").containsKey("processing_plan"), "pot changing ingredients invalidates work and snapshot");
        flex(MISSING, 1);
        PotController missing = appliance("pot", PotController.class);
        missing.addIngredient(item(INPUT), true, null); missing.setHasOil(true); missing.stirFry(true, null);
        for (int i = 0; i < 30; i++) missing.tick();
        check(missing.stage() == PotStage.COOKING && missing.ingredients().size() == 1 && missing.hasOil()
                && data(missing, "cooking_pot").getBoolean("processing_blocked", false), "pot missing output blocks once and retains input/oil");
        CompoundTag corrupt = saved;
        corrupt.getCompound("kaleidoscopecookery:cooking_pot").getCompound("processing_plan").putInt("version", 999);
        PotController invalid = appliance("pot", PotController.class); invalid.loadCustomData(corrupt);
        for (int i = 0; i < 24; i++) invalid.tick();
        check(invalid.ingredients().size() == 1 && data(invalid, "cooking_pot").getBoolean("processing_blocked", false),
                "pot corrupt persisted plan preserves ingredients");
        ((PotElement) get(pot, "element")).deactivate();
        ((PotElement) get(restored, "element")).deactivate();
    }

    private void choppingCases() throws Exception {
        chopping(BREAD, 3);
        ChoppingBoardController board = appliance("chopping_board", ChoppingBoardController.class);
        check(board.place(item(INPUT)), "chopping input accepted");
        check(board.currentStageModel().equals("minecraft:bread"), "chopping freezes first model");
        check(board.cut() == ChoppingBoardController.CutResult.ADVANCED, "chopping first cut advances");
        CompoundTag saved = save(board);
        chopping(CARROT, 1);
        ChoppingBoardController restored = appliance("chopping_board", ChoppingBoardController.class);
        restored.loadCustomData(saved);
        check(saved.equals(save(restored)), "chopping NBT preserves stage, model, count and chosen result");
        check(plan(restored, "chopping_board").workRequired() == 3
                && plan(restored, "chopping_board").outputs().getFirst().key().equals(BREAD), "chopping loaded snapshot ignores changed recipe");
        check(restored.cut() == ChoppingBoardController.CutResult.ADVANCED, "chopping second cut keeps original three-cut threshold");
        check(restored.currentStageModel().equals("minecraft:apple"), "chopping later display model remains frozen after edit");
        check(restored.cut() == ChoppingBoardController.CutResult.FINISHED && restored.isEmpty(), "chopping third cut commits frozen result");
        ChoppingBoardController next = appliance("chopping_board", ChoppingBoardController.class); next.place(item(INPUT));
        check(plan(next, "chopping_board").workRequired() == 1 && plan(next, "chopping_board").outputs().getFirst().key().equals(CARROT),
                "next chopping batch uses changed knife count and output");
        chopping(MISSING, 1);
        ChoppingBoardController blocked = appliance("chopping_board", ChoppingBoardController.class); blocked.place(item(INPUT));
        check(blocked.cut() == ChoppingBoardController.CutResult.NOTHING && blocked.placedItem().id().equals(INPUT),
                "chopping unavailable output preserves raw food");
        check(blocked.takeBack().id().equals(INPUT) && blocked.isEmpty(), "paused chopping input remains recoverable");
        checks.add("chopping has no completion event API; cancellation acceptance covers actual pot/millstone events");
    }

    private void millstoneCases() throws Exception {
        accurate(BREAD, 2);
        Key furnitureId = Key.of("kaleidoscopecookery:new_millstone");
        check(CraftEngineFurniture.byId(furnitureId) != null, "real pack new_millstone furniture definition is registered");
        BukkitFurniture mill = CraftEngineFurniture.place(new Location(world, 12.5, 100, 12.5), furnitureId, "ground", false);
        if (mill == null) throw new AssertionError("real configured millstone furniture missing");
        furniture.add(mill);
        MillstoneController controller = mill.controller.get(MillstoneController.class, 0);
        if (controller == null) throw new AssertionError("real millstone controller missing");
        check(controller.tryAddGrind(item(INPUT)), "millstone accepts first slot");
        call(controller, "advanceGrind", 180f);
        CompoundTag saved = save(controller);
        CompoundTag first = (CompoundTag) saved.getCompound("kaleidoscopecookery:millstone").getList("grind_items").get(0);
        check(first.getFloat("progress_degrees", 0) == 180f && CookingPlan.load(first.getCompound("processing_plan")).workRequired() == 2,
                "millstone saves independent angle and frozen rotations");
        accurate(CARROT, 1);
        controller.loadCustomData(saved);
        check(saved.equals(save(controller)), "millstone NBT restores plan, output, progress and rotations");
        check(controller.tryAddGrind(item(INPUT)), "millstone accepts second slot after recipe edit");
        CompoundTag slots = save(controller).getCompound("kaleidoscopecookery:millstone");
        CompoundTag second = (CompoundTag) slots.getList("grind_items").get(1);
        check(CookingPlan.load(second.getCompound("processing_plan")).workRequired() == 1
                && CookingPlan.load(first.getCompound("processing_plan")).outputs().getFirst().key().equals(BREAD),
                "millstone slots retain distinct batch thresholds and outputs");
        controller.stopSpinning();
        CompoundTag stopped = save(controller);
        for (int i = 0; i < 10; i++) controller.tick();
        check(stopped.equals(save(controller)), "millstone without animal/pusher does not increment progress");
        listener.cancelGrind = true;
        call(controller, "advanceGrind", 540f);
        check(!controller.grindItem(0).isEmpty() && !controller.grindItem(1).isEmpty(), "cancelled millstone completion retains both raw slots");
        int events = listener.grindEvents;
        call(controller, "advanceGrind", 360f);
        check(listener.grindEvents == events, "paused cancelled millstone slots do not rebuild or re-emit completion");
        listener.cancelGrind = false;
        controller.takeGrind(null, net.momirealms.craftengine.core.entity.player.InteractionHand.MAIN_HAND);
        controller.takeGrind(null, net.momirealms.craftengine.core.entity.player.InteractionHand.MAIN_HAND);
        check(controller.grindIsEmpty(), "cancelled millstone slots can be recovered");
        accurate(MISSING, 1);
        controller.tryAddGrind(item(INPUT)); call(controller, "advanceGrind", 360f);
        check(controller.grindItem(0).id().equals(INPUT), "millstone missing frozen result preserves raw input");
        check(listener.stirEvents >= 2 && listener.grindEvents >= 1, "real Bukkit cancellation hooks were exercised");
    }

    private <T extends BlockEntityController> T appliance(String id, Class<T> type) {
        var definition = CraftEngineBlocks.byId(Key.of("kaleidoscopecookery:" + id));
        if (definition == null) throw new AssertionError("Missing configured appliance " + id);
        BlockEntity entity = new BlockEntity(new BlockPos(8, 100, 12), definition.defaultState());
        entity.setWorld(ceWorld);
        T controller = entity.controller.getAt(type, 0);
        if (controller == null) throw new AssertionError("Missing configured controller " + type);
        controllers.add(controller);
        return controller;
    }
    private void flex(Key result, int stirs) {
        FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
        registry.removeFlex(ApplianceType.POT, id("pot"));
        registry.registerFlex(FlexFoodRecipe.of(id("pot"), result, ApplianceType.POT, Map.of(INPUT, 1), List.of(), null, false, false, 0, stirs));
    }
    private void chopping(Key result, int cuts) {
        FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
        registry.removeChopping(id("chopping"));
        registry.registerChopping(new ChoppingBoardRecipe(id("chopping"), INPUT, cuts,
                cuts > 1 ? List.of("minecraft:bread", "minecraft:carrot", "minecraft:apple") : List.of("minecraft:carrot"), ChoppingMode.SINGLE,
                List.of(new ChoppingResult(result, 1, 1)), List.of()));
    }
    private void accurate(Key result, int rotations) {
        FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
        registry.removeAccurate(id("millstone"));
        registry.registerAccurate(new AccurateFoodRecipe(id("millstone"), INPUT, List.of(new WeightedResult(result, 1)),
                ApplianceType.MILLSTONE, rotations, 1, List.of()));
    }
    private void remember(int x, int y, int z, Material material) {
        BlockPos position = new BlockPos(x, y, z);
        before.putIfAbsent(position, world.getBlockAt(x, y, z).getBlockData().clone());
        world.getBlockAt(x, y, z).setType(material, false);
    }
    private void cleanup() {
        HandlerList.unregisterAll(listener);
        controllers.forEach(controller -> controller.gatherElements(element -> element.deactivate()));
        for (BukkitFurniture value : furniture) CraftEngineFurniture.remove(value, false, false);
        world.getEntities().stream().filter(entity -> !originalEntities.contains(entity.getUniqueId())
                && entity instanceof org.bukkit.entity.Item && entity.getLocation().distanceSquared(new Location(world, 10, 100, 12)) < 100)
                .forEach(org.bukkit.entity.Entity::remove);
        if (before.containsKey(new BlockPos(8, 100, 12))) CraftEngineBlocks.remove(world.getBlockAt(8, 100, 12), false);
        before.forEach((position, data) -> world.getBlockAt(position.x(), position.y(), position.z()).setBlockData(data, false));
        FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
        registry.removeFlex(ApplianceType.POT, id("pot")); registry.removeChopping(id("chopping")); registry.removeAccurate(id("millstone"));
        originalAllowed.forEach((type, allowed) -> { if (!allowed) ApplianceFoodRegistry.instance().unregister(type, INPUT); });
    }
    private void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks.add("interactive: " + label);
        plugin.getLogger().info("PASS: " + label);
    }
    private static Key id(String value) { return Key.of("interactive_processing_probe:" + value); }
    private static Item item(Key key) { return InventoryUtils.createOrEmpty(key); }
    private static CompoundTag save(Object controller) throws Exception {
        CompoundTag tag = new CompoundTag(); call(controller, "saveCustomData", tag); return tag;
    }
    private static CompoundTag data(Object controller, String appliance) throws Exception { return save(controller).getCompound("kaleidoscopecookery:" + appliance); }
    private static CookingPlan plan(Object controller, String appliance) throws Exception { return CookingPlan.load(data(controller, appliance).getCompound("processing_plan")); }
    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static Object call(Object target, String name, Object... args) throws Exception {
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            method.setAccessible(true);
            try { return method.invoke(target, args); }
            catch (InvocationTargetException error) {
                if (error.getCause() instanceof Exception exception) throw exception;
                if (error.getCause() instanceof Error failure) throw failure;
                throw error;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }
}
