import net.kaleidoscope.cookery.block.entity.MillstoneController;
import net.kaleidoscope.cookery.util.FoliaUtil;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import net.momirealms.craftengine.bukkit.world.BukkitWorldManager;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.scheduler.SchedulerTask;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Donkey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Actual Folia ownership rejection, plus explicitly controlled movement-result acceptance.
 * Controlled result completion never claims that a real teleport was successful.
 */
public final class FoliaBoundaryProbe extends JavaPlugin {
    private static final Key INPUT = Key.of("minecraft:echo_shard");
    private static final Key MILLSTONE = Key.of("kaleidoscopecookery:new_millstone");
    private static final int OWNER_X = 10;
    private static final int OWNER_Z = 6;
    private static final int REMOTE_BLOCK = 4096;
    private static final int REMOTE_CHUNK = REMOTE_BLOCK >> 4;
    private static final int REMOTE_ACTIVATION_ATTEMPTS = 100;
    private final List<String> checks = Collections.synchronizedList(new ArrayList<>());
    private final List<String> completionThreads = Collections.synchronizedList(new ArrayList<>());
    private final Map<Block, BlockData> blocksBefore = new LinkedHashMap<>();
    private final Map<String, Object> placementDiagnostics = new LinkedHashMap<>();
    private final Map<String, Object> entityDiagnostics = new LinkedHashMap<>();
    private final AtomicBoolean cleanupStarted = new AtomicBoolean();
    private final AtomicBoolean cleanupSucceeded = new AtomicBoolean(true);
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private World world;
    private Location owner;
    private Location remote;
    private BukkitFurniture furniture;
    private MillstoneController controller;
    private volatile Cow cow;
    private volatile Donkey donkey;
    private volatile Location cowBefore;
    private volatile Location donkeyBefore;
    private volatile ItemStack[] inventoryBefore;
    private volatile boolean farForceLoadedBefore;
    private volatile boolean farWasPrepared;
    private volatile boolean ownerForceLoadedBefore;
    private volatile boolean ownerWasPrepared;
    private volatile CompletableFuture<Void> ownerSetup = CompletableFuture.completedFuture(null);
    private volatile CompletableFuture<Void> remoteSetup = CompletableFuture.completedFuture(null);
    private SchedulerTask timeout;
    private int placementAttempts;
    private volatile boolean remoteEntitiesConfirmed;

    @Override public void onEnable() {
        String name = System.getProperty("cookery.processing.world", "processing-verification");
        world = Bukkit.getWorld(name);
        if (world == null) {
            finish(new IllegalStateException("Acceptance world is unavailable: " + name));
            return;
        }
        owner = new Location(world, OWNER_X, 100, OWNER_Z);
        remote = new Location(world, REMOTE_BLOCK, 100, REMOTE_BLOCK);
        // Earlier acceptance plugins use 100 and 300 ticks, including a real configuration reload.
        FoliaUtil.runLater(() -> guarded(this::start), 600L, owner);
    }

    private void start() throws Exception {
        check(Boolean.getBoolean("cookery.processing.acceptance"), "dedicated acceptance profile flag");
        check(FoliaUtil.isFolia(), "actual Folia server runtime");
        check(Bukkit.isOwnedByCurrentRegion(owner), "millstone fixture starts in its owning region");
        check(!world.getName().equals("world") && Bukkit.getOnlinePlayers().isEmpty(), "isolated world with no online players");
        check(System.getProperty("cookery.processing.craftengine", "missing").equals(ceVersion()), "exact CraftEngine dependency version");
        check(Bukkit.getPluginManager().isPluginEnabled("KaleidoscopeCookeryPlugin"), "cookery plugin enabled");
        timeout = FoliaUtil.runLater(() -> finish(new TimeoutException("Folia boundary verification timed out")), 1800L, owner);
        ownerSetup = globalAction(() -> {
            if (cleanupStarted.get()) return;
            ownerForceLoadedBefore = world.isChunkForceLoaded(0, 0);
            ownerWasPrepared = true;
            world.setChunkForceLoaded(0, 0, true);
            checks.add("near chunk ticket read and update complete on CE global scheduler");
        });
        ownerSetup.whenComplete((ignored, error) -> {
            if (error != null) finish(error);
            else atOwner(this::prepareOwnerFixture);
        });
    }

    private void prepareOwnerFixture() throws Exception {
        var definition = CraftEngineFurniture.byId(MILLSTONE);
        placementDiagnostics.put("millstone_definition_present", definition != null);
        placementDiagnostics.put("millstone_variants", definition == null ? List.of() : List.copyOf(definition.variants().keySet()));
        placementDiagnostics.put("loaded_furniture_count", CraftEngineFurniture.loadedFurniture().size());
        placementDiagnostics.put("loaded_blocks_count", CraftEngineBlocks.loadedBlocks().size());
        placementDiagnostics.put("before_preparation", capturePlacementBlocks());
        Location downward = owner.clone().add(0.5, 2.5, 0.5);
        downward.setPitch(90f);
        BukkitFurniture existing = CraftEngineFurniture.rayTrace(downward, 5.0);
        placementDiagnostics.put("existing_logical_furniture_id", existing == null ? null : existing.config.id().asString());
        check(definition != null, "real configured new_millstone definition remains registered before boundary placement");
        check(definition.getVariant("ground") != null, "real configured new_millstone retains ground variant");
        check(existing == null, "fresh boundary position contains no unknown logical furniture");
        check(placementBlocksAreUnoccupied(), "fresh boundary physical blocks contain no unknown CE state or block entity");
        for (int x = OWNER_X - 2; x <= OWNER_X + 2; x++) for (int z = OWNER_Z - 2; z <= OWNER_Z + 2; z++)
            rememberAndSet(x, 99, z, Material.STONE);
        placementDiagnostics.put("before_placement", capturePlacementBlocks());
        placementAttempts++;
        furniture = CraftEngineFurniture.place(owner.clone().add(0.5, 0, 0.5), MILLSTONE, "ground", false);
        placementDiagnostics.put("placement_returned_furniture", furniture != null);
        placementDiagnostics.put("after_placement", capturePlacementBlocks());
        check(furniture != null, "real configured millstone furniture placed in owner region; attempts=" + placementAttempts
                + "; target=" + (OWNER_X + 0.5) + ",100," + (OWNER_Z + 0.5));
        controller = furniture.controller.get(MillstoneController.class, 0);
        check(controller != null && controller.tryAddGrind(InventoryUtils.createOrEmpty(INPUT)), "real millstone controller retains fixture raw input");
        check(progress() == 0f, "fixture starts without completed grinding work");

        int unloadedX = REMOTE_CHUNK, unloadedZ = REMOTE_CHUNK;
        if (world.isChunkLoaded(unloadedX, unloadedZ)) { unloadedX *= 2; unloadedZ *= 2; }
        check(!world.isChunkLoaded(unloadedX, unloadedZ), "unloaded boundary target starts unloaded");
        check(!(boolean) invoke(controller, "canStandAt", new Class<?>[]{double.class, double.class, double.class},
                unloadedX * 16.0 + 0.5, 100.0, unloadedZ * 16.0 + 0.5), "canStandAt rejects foreign unloaded target");
        check(!world.isChunkLoaded(unloadedX, unloadedZ), "canStandAt does not load its rejected target chunk");
        check(controller.grindItem(0).id().equals(INPUT) && progress() == 0f, "chunk-boundary rejection preserves raw material and progress");

        remoteSetup = new CompletableFuture<>();
        CompletableFuture<org.bukkit.Chunk> remoteChunk;
        try { remoteChunk = world.getChunkAtAsync(REMOTE_CHUNK, REMOTE_CHUNK, true); }
        catch (RuntimeException | Error problem) {
            remoteSetup.completeExceptionally(problem);
            throw problem;
        }
        remoteChunk.whenComplete((chunk, error) -> {
            if (error != null) { remoteSetup.completeExceptionally(error); finish(error); return; }
            globalAction(() -> {
                if (cleanupStarted.get()) return;
                farForceLoadedBefore = world.isChunkForceLoaded(REMOTE_CHUNK, REMOTE_CHUNK);
                farWasPrepared = true;
                world.setChunkForceLoaded(REMOTE_CHUNK, REMOTE_CHUNK, true);
                checks.add("remote chunk ticket read and update complete on CE global scheduler");
            }).whenComplete((ticketReady, ticketError) -> {
                if (ticketError != null) { remoteSetup.completeExceptionally(ticketError); finish(ticketError); return; }
                // FULL generation completion does not imply that the new global ticket has activated tracking.
                try { FoliaUtil.runLater(this::spawnFarAnimals, 2L, remote); }
                catch (Throwable problem) { remoteSetup.completeExceptionally(problem); finish(problem); }
            });
        });
    }

    private void spawnFarAnimals() {
        try {
            if (cleanupStarted.get()) { remoteSetup.complete(null); return; }
            check(Bukkit.isOwnedByCurrentRegion(remote), "remote animals spawn on the remote owner region");
            cow = (Cow) world.spawnEntity(remote.clone().add(0.5, 0, 0.5), EntityType.COW);
            cow.setAI(false); cow.setGravity(false); cow.setInvulnerable(true); cow.setAdult();
            donkey = (Donkey) world.spawnEntity(remote.clone().add(2.5, 0, 2.5), EntityType.DONKEY);
            donkey.setAI(false); donkey.setGravity(false); donkey.setInvulnerable(true); donkey.setAdult();
            donkey.setCarryingChest(true);
            donkey.getInventory().setItem(2, new ItemStack(Material.POTATO, 3));
            entityDiagnostics.put("immediately_after_spawn", remoteEntityState());
            // CraftEntity.isValid becomes true on onTrackingStart, which may follow a later chunk-status update.
            FoliaUtil.runLater(() -> awaitRemoteAnimalActivation(1), 2L, remote);
        } catch (Throwable problem) {
            remoteSetup.completeExceptionally(problem);
            finish(problem);
        }
    }

    private void awaitRemoteAnimalActivation(int attempt) {
        try {
            if (cleanupStarted.get()) { remoteSetup.complete(null); return; }
            check(Bukkit.isOwnedByCurrentRegion(remote) && Bukkit.isOwnedByCurrentRegion(cow)
                    && Bukkit.isOwnedByCurrentRegion(donkey), "remote activation check uses the actual entity owner region");
            entityDiagnostics.put("activation_attempts", attempt);
            entityDiagnostics.put("last_activation_state", remoteEntityState());
            check(!cow.isDead() && !donkey.isDead(), "remote fixture animals were not removed or spawn-cancelled before activation");
            if (!cow.isValid() || !donkey.isValid() || !cow.isInWorld() || !donkey.isInWorld()) {
                if (attempt >= REMOTE_ACTIVATION_ATTEMPTS) throw new AssertionError(
                        "actual cow and chest donkey were not tracked within bounded region ticks; states=" + entityDiagnostics);
                FoliaUtil.runLater(() -> awaitRemoteAnimalActivation(attempt + 1), 2L, remote);
                return;
            }
            CompletableFuture<Void> cowReady = entityAction(cow, () -> {
                check(cow.isValid() && cow.isInWorld(), "actual cow exists after tracking activation on its entity scheduler");
                cowBefore = cow.getLocation();
            });
            CompletableFuture<Void> donkeyReady = entityAction(donkey, () -> {
                check(donkey.isValid() && donkey.isInWorld(), "actual chest donkey exists after tracking activation on its entity scheduler");
                donkeyBefore = donkey.getLocation();
                inventoryBefore = copyInventory(donkey.getInventory().getContents());
            });
            CompletableFuture.allOf(cowReady, donkeyReady).whenComplete((ignored, error) -> {
                if (error != null) { remoteSetup.completeExceptionally(error); finish(error); return; }
                remoteEntitiesConfirmed = true;
                checks.add("actual cow and chest donkey exist in remote region after owner-scheduler activation confirmation");
                remoteSetup.complete(null);
                atOwner(this::foreignAnimalCases);
            });
        } catch (Throwable problem) {
            remoteSetup.completeExceptionally(problem);
            finish(problem);
        }
    }

    private Map<String, Object> remoteEntityState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("chunk_loaded", world.isChunkLoaded(REMOTE_CHUNK, REMOTE_CHUNK));
        state.put("cow", entityState(cow));
        state.put("donkey", entityState(donkey));
        return state;
    }
    private Map<String, Object> entityState(Entity entity) {
        Map<String, Object> state = new LinkedHashMap<>();
        boolean owned = Bukkit.isOwnedByCurrentRegion(entity);
        state.put("owned_by_current_region", owned);
        if (!owned) return state;
        state.put("uuid", entity.getUniqueId().toString());
        state.put("entity_id", entity.getEntityId());
        state.put("valid", entity.isValid());
        state.put("in_world", entity.isInWorld());
        state.put("dead", entity.isDead());
        state.put("ticks_lived", entity.getTicksLived());
        return state;
    }

    private void foreignAnimalCases() throws Exception {
        check(Bukkit.isOwnedByCurrentRegion(owner), "cross-region rejection runs at furniture owner");
        check(!Bukkit.isOwnedByCurrentRegion(cow) && !Bukkit.isOwnedByCurrentRegion(donkey), "actual animal entities belong to different region");
        check(!(boolean) invoke(controller, "canStandAt", new Class<?>[]{double.class, double.class, double.class},
                REMOTE_BLOCK + 0.5, 100.0, REMOTE_BLOCK + 0.5), "canStandAt also rejects a loaded foreign region");
        check(!(boolean) get(controller, "animating"), "millstone is idle before foreign animal offers");
        check(!controller.spinWithAnimal(cow, null, true), "actual foreign cow is rejected without crossing ownership boundary");
        check(!controller.spinWithAnimal(donkey, null, true), "actual foreign chest donkey is rejected without crossing ownership boundary");
        check(get(controller, "pullingAnimal") == null && get(controller, "animalMovement") == null,
                "rejected remote offers do not bind animal or movement state");
        check(controller.grindItem(0).id().equals(INPUT) && progress() == 0f, "foreign animal offers preserve existing raw input and progress");
        CompletableFuture<Void> cowCheck = entityAction(cow, () -> {
            check(cow.getLocation().distanceSquared(cowBefore) < 1.0e-9, "rejected cow position remains unchanged on its owner thread");
            check(!cow.hasAI() && !cow.hasGravity(), "rejected cow ownership offer preserves fixture state");
        });
        CompletableFuture<Void> donkeyCheck = entityAction(donkey, () -> {
            check(donkey.getLocation().distanceSquared(donkeyBefore) < 1.0e-9, "rejected donkey position remains unchanged on its owner thread");
            check(Arrays.equals(inventoryBefore, donkey.getInventory().getContents()), "rejected donkey offer preserves its actual chest inventory");
        });
        CompletableFuture.allOf(cowCheck, donkeyCheck).whenComplete((ignored, error) -> {
            if (error != null) finish(error);
            else atOwner(this::pendingMovementCase);
        });
    }

    private void pendingMovementCase() throws Exception {
        controller.onUnload(); // Suspend the registered ticker while the controlled result is pending.
        Object movement = movement(45f, 45f);
        set(controller, "animalMovement", movement);
        check(!(boolean) invoke(controller, "consumeAnimalMovement", new Class<?>[0]), "pending controlled movement cannot commit work");
        check(progress() == 0f && controller.grindItem(0).id().equals(INPUT), "pending controlled movement preserves raw input and progress");
        completeControlled(movement, false, () -> {
            check(progress() == 0f, "asynchronous failed result changes no owner progress before consumption");
            set(controller, "loaded", true);
            check(!(boolean) invoke(controller, "consumeAnimalMovement", new Class<?>[0]), "failed controlled movement is consumed without success");
            check(progress() == 0f && controller.grindItem(0).id().equals(INPUT), "failed controlled movement preserves raw input and work");
            successfulMovementCase();
        });
    }

    private void successfulMovementCase() throws Exception {
        controller.onUnload();
        Object movement = movement(45f, 45f);
        set(controller, "animalMovement", movement);
        check(!(boolean) invoke(controller, "consumeAnimalMovement", new Class<?>[0]), "controlled successful case initially remains pending");
        completeControlled(movement, true, () -> {
            check(progress() == 0f && (float) get(controller, "currentAngle") == 0f,
                    "successful result completion only sets result flags until owner commits");
            set(controller, "loaded", true);
            check((boolean) invoke(controller, "consumeAnimalMovement", new Class<?>[0]), "owner consumes the confirmed controlled success");
            check(progress() == 45f && (float) get(controller, "currentAngle") == 45f,
                    "confirmed controlled success advances angle and raw-slot progress once on owner");
            check(controller.grindItem(0).id().equals(INPUT), "partial confirmed work preserves the original raw material");
            lateUnloadCase();
        });
    }

    private void lateUnloadCase() throws Exception {
        Object late = movement(90f, 45f);
        set(controller, "animalMovement", late);
        controller.onUnload();
        completeControlled(late, true, () -> {
            check(!(boolean) get(controller, "loaded") && get(controller, "animalMovement") == null,
                    "unload invalidates the old controlled movement reference");
            controller.tick();
            check(progress() == 45f, "late controlled completion after unload cannot advance an inactive controller");
            controller.onLoad();
            CompoundTag saved = new CompoundTag();
            controller.saveCustomData(saved);
            Object oldGeneration = movement(90f, 45f);
            set(controller, "animalMovement", oldGeneration);
            controller.onUnload();
            controller.loadCustomData(saved);
            controller.onLoad();
            completeControlled(oldGeneration, true, () -> {
                check(get(controller, "animalMovement") == null, "reload retains no pending movement from the previous lifecycle");
                controller.tick();
                check(progress() == 45f && controller.grindItem(0).id().equals(INPUT),
                        "late previous-lifecycle result cannot change restored work or raw material");
                finish(null);
            });
        });
    }

    private void completeControlled(Object movement, boolean success, CheckedAction after) {
        CraftEngine.instance().scheduler().executeAsync(() -> {
            try {
                check(!Bukkit.isOwnedByCurrentRegion(owner), "controlled completion runs away from furniture owner region");
                invoke(movement, "finish", new Class<?>[]{boolean.class}, success);
                completionThreads.add(Thread.currentThread().getName());
                atOwner(after);
            } catch (Throwable error) { finish(error); }
        });
    }

    private CompletableFuture<Void> entityAction(Entity entity, CheckedAction action) {
        return entityAction(entity, action, false);
    }
    private CompletableFuture<Void> entityAction(Entity entity, CheckedAction action, boolean allowRetired) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        try {
            FoliaUtil.run(() -> {
                try {
                    check(Bukkit.isOwnedByCurrentRegion(entity), "known entity operation uses its owner scheduler");
                    action.run(); done.complete(null);
                } catch (Throwable problem) { done.completeExceptionally(problem); }
            }, () -> {
                if (allowRetired) done.complete(null);
                else done.completeExceptionally(new IllegalStateException("Fixture entity retired before its owner-thread assertion"));
            }, entity);
        } catch (Throwable problem) { done.completeExceptionally(problem); }
        return done;
    }
    private CompletableFuture<Void> globalAction(CheckedAction action) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        try {
            CraftEngine.instance().scheduler().platform().run(() -> {
                try { action.run(); done.complete(null); }
                catch (Throwable problem) { done.completeExceptionally(problem); }
            });
        } catch (Throwable problem) { done.completeExceptionally(problem); }
        return done;
    }
    private void atOwner(CheckedAction action) {
        if (cleanupStarted.get()) return;
        FoliaUtil.run(() -> guarded(() -> {
            check(Bukkit.isOwnedByCurrentRegion(owner), "continuation returns to furniture owner region");
            action.run();
        }), owner);
    }
    private void guarded(CheckedAction action) {
        if (cleanupStarted.get()) return;
        try { action.run(); }
        catch (Throwable problem) { finish(problem); }
    }
    private Object movement(float target, float degrees) throws Exception {
        Class<?> type = Class.forName(MillstoneController.class.getName() + "$AnimalMovement");
        Constructor<?> constructor = type.getDeclaredConstructor(float.class, float.class);
        constructor.setAccessible(true);
        return constructor.newInstance(target, degrees);
    }
    private float progress() {
        CompoundTag saved = new CompoundTag();
        controller.saveCustomData(saved);
        var items = saved.getCompound("kaleidoscopecookery:millstone").getList("grind_items");
        return items.isEmpty() ? -1f : ((CompoundTag) items.get(0)).getFloat("progress_degrees", -1f);
    }
    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true);
        try { return method.invoke(target, arguments); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception cause) throw cause;
            if (error.getCause() instanceof Error cause) throw cause;
            throw error;
        }
    }
    private void rememberAndSet(int x, int y, int z, Material type) {
        Block block = world.getBlockAt(x, y, z);
        blocksBefore.putIfAbsent(block, block.getBlockData().clone()); block.setType(type, false);
    }
    private Map<String, Object> capturePlacementBlocks() {
        Map<String, Object> observed = new LinkedHashMap<>();
        var storage = BukkitWorldManager.instance().getStorageWorld(world);
        for (int x = OWNER_X - 2; x <= OWNER_X + 2; x++) for (int z = OWNER_Z - 2; z <= OWNER_Z + 2; z++)
            for (int y = 99; y <= 101; y++) {
                Block block = world.getBlockAt(x, y, z);
                var pos = new BlockPos(x, y, z);
                var state = storage == null ? null : storage.getBlockStateAtIfLoaded(pos);
                var blockEntity = storage == null ? null : storage.getBlockEntityAtIfLoaded(pos, false);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("bukkit_data", block.getBlockData().getAsString());
                entry.put("bukkit_tile_entity", block.getState() instanceof TileState);
                entry.put("custom_block", CraftEngineBlocks.isCustomBlock(block));
                entry.put("logical_ce_state", state == null || state.isEmpty() ? null : state.toString());
                entry.put("logical_block_entity", blockEntity == null ? null : blockEntity.getClass().getName());
                observed.put(x + "," + y + "," + z, entry);
            }
        return observed;
    }
    private boolean placementBlocksAreUnoccupied() {
        var storage = BukkitWorldManager.instance().getStorageWorld(world);
        for (int x = OWNER_X - 2; x <= OWNER_X + 2; x++) for (int z = OWNER_Z - 2; z <= OWNER_Z + 2; z++)
            for (int y = 99; y <= 101; y++) {
                Block block = world.getBlockAt(x, y, z);
                var pos = new BlockPos(x, y, z);
                var state = storage == null ? null : storage.getBlockStateAtIfLoaded(pos);
                if (block.getState() instanceof TileState || CraftEngineBlocks.isCustomBlock(block) || (state != null && !state.isEmpty())
                        || (storage != null && storage.getBlockEntityAtIfLoaded(pos, false) != null)) return false;
            }
        return true;
    }
    private static ItemStack[] copyInventory(ItemStack[] source) {
        ItemStack[] result = source.clone();
        for (int i = 0; i < result.length; i++) if (result[i] != null) result[i] = result[i].clone();
        return result;
    }
    private void check(boolean success, String description) {
        if (!success) throw new AssertionError(description);
        checks.add(description);
    }

    private void finish(Throwable problem) {
        if (problem != null) rememberFailure(problem);
        if (!cleanupStarted.compareAndSet(false, true)) return;
        if (timeout != null) timeout.cancel();
        CompletableFuture.allOf(ownerSetup, remoteSetup).whenComplete((ignored, setupError) -> {
            if (setupError != null) rememberFailure(setupError);
            List<CompletableFuture<Void>> removals = new ArrayList<>();
            if (cow != null) removals.add(entityAction(cow, cow::remove, true));
            if (donkey != null) removals.add(entityAction(donkey, donkey::remove, true));
            CompletableFuture.allOf(removals.toArray(CompletableFuture<?>[]::new)).whenComplete((removed, removeError) -> {
                if (removeError != null) { cleanupSucceeded.set(false); rememberFailure(removeError); }
                cleanupOwner();
            });
        });
    }

    private void cleanupOwner() {
        if (owner == null) { writeReport(false); return; }
        FoliaUtil.run(() -> {
            try {
                if (controller != null) {
                    controller.stopSpinning();
                    for (int i = 0; i < MillstoneController.GRIND_SLOTS && !controller.grindIsEmpty(); i++)
                        controller.takeGrind(null, InteractionHand.MAIN_HAND);
                    controller.onUnload();
                }
                if (furniture != null) CraftEngineFurniture.remove(furniture, false, false);
            } catch (Throwable error) { cleanupSucceeded.set(false); rememberFailure(error); }
            try {
                blocksBefore.forEach((block, data) -> block.setBlockData(data, false));
            } catch (Throwable error) { cleanupSucceeded.set(false); rememberFailure(error); }
            globalAction(() -> {
                Throwable restoreFailure = null;
                try { if (farWasPrepared) world.setChunkForceLoaded(REMOTE_CHUNK, REMOTE_CHUNK, farForceLoadedBefore); }
                catch (Throwable error) { restoreFailure = error; }
                try { if (ownerWasPrepared) world.setChunkForceLoaded(0, 0, ownerForceLoadedBefore); }
                catch (Throwable error) {
                    if (restoreFailure == null) restoreFailure = error;
                    else restoreFailure.addSuppressed(error);
                }
                if (restoreFailure != null) throw new IllegalStateException("Cannot restore temporary global chunk tickets", restoreFailure);
                checks.add("known animals, owned furniture, modified blocks and global chunk tickets are cleaned before reporting");
            }).whenComplete((restored, error) -> {
                if (error != null) { cleanupSucceeded.set(false); rememberFailure(error); }
                writeReport(cleanupSucceeded.get());
            });
        }, owner);
    }
    private void rememberFailure(Throwable problem) {
        Throwable first = failure.get();
        if (first == null && failure.compareAndSet(null, problem)) return;
        first = failure.get();
        if (first != problem) first.addSuppressed(problem);
    }
    private String ceVersion() {
        var ce = Bukkit.getPluginManager().getPlugin("CraftEngine");
        return ce == null ? "missing" : ce.getDescription().getVersion();
    }
    private void writeReport(boolean cleanupComplete) {
        Throwable problem = failure.get();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("passed", problem == null && cleanupComplete);
        report.put("craftengine", ceVersion());
        report.put("server", Bukkit.getVersion());
        report.put("folia", FoliaUtil.isFolia());
        report.put("scope", "Actual cross-region Folia rejection using real cow, chest donkey and millstone; controlled AnimalMovement results test commit/lifecycle logic; no real successful teleport is claimed");
        report.put("actual_foreign_entities", cow != null && donkey != null);
        report.put("remote_entities_confirmed", remoteEntitiesConfirmed);
        report.put("controlled_completion_only", true);
        report.put("real_teleport_success_tested", false);
        report.put("cleanup_completed_before_report", cleanupComplete);
        report.put("world", world == null ? null : world.getName());
        report.put("remote_block", REMOTE_BLOCK);
        report.put("remote_chunk", REMOTE_CHUNK);
        report.put("placement_attempts", placementAttempts);
        report.put("placement_diagnostics", placementDiagnostics);
        report.put("remote_entity_diagnostics", entityDiagnostics);
        report.put("check_count", checks.size());
        report.put("checks", List.copyOf(checks));
        report.put("completion_threads", List.copyOf(completionThreads));
        report.put("error", problem == null ? null : problem.toString());
        String json = json(report);
        if (problem == null) getLogger().info("FOLIA_BOUNDARY_PROBE_PASS " + checks.size() + " checks");
        else getLogger().log(java.util.logging.Level.SEVERE, "FOLIA_BOUNDARY_PROBE_FAIL", problem);
        CraftEngine.instance().scheduler().executeAsync(() -> {
            try {
                Files.createDirectories(getDataFolder().toPath());
                Files.writeString(getDataFolder().toPath().resolve("folia-boundary-result.json"), json, StandardCharsets.UTF_8);
            } catch (Exception error) { getLogger().log(java.util.logging.Level.SEVERE, "Cannot write Folia boundary report", error); }
        });
    }
    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) return map.entrySet().stream()
                .map(entry -> quote(entry.getKey().toString()) + ":" + json(entry.getValue()))
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        if (value instanceof Iterable<?> entries) {
            List<String> values = new ArrayList<>(); entries.forEach(item -> values.add(json(item)));
            return String.join(",", values).transform(text -> '[' + text + ']');
        }
        return quote(value.toString());
    }
    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + '"';
    }
}
