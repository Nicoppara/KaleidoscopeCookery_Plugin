import net.kaleidoscope.cookery.block.behavior.StoveBehavior;
import net.kaleidoscope.cookery.block.entity.TeapotBar;
import net.kaleidoscope.cookery.block.entity.TeapotController;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.FoliaUtil;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.event.CustomBlockAttemptPlaceEvent;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.bukkit.world.BukkitWorldManager;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.scheduler.SchedulerTask;
import net.momirealms.craftengine.core.registry.BuiltInRegistries;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Real registered event listeners and real CE blocks/controllers; players are explicitly simulated. */
public final class TeapotPlacementProbe extends JavaPlugin {
    private static final Key TEAPOT = Key.of("kaleidoscopecookery:teapot");
    private static final Key STOVE = Key.of("kaleidoscopecookery:stove");
    private final List<String> checks = new ArrayList<>();
    private final List<Map<String, Object>> cases = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private final Map<Block, BlockData> originals = new LinkedHashMap<>();
    private final Map<Block, Key> ownedBlocks = new LinkedHashMap<>();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final AtomicReference<RuntimeException> proxyFailure = new AtomicReference<>();
    private final ItemStack[] inventory = new ItemStack[41];
    private World world;
    private CEWorld storage;
    private Location owner, tankLocation;
    private Plugin farmers, fluidCore;
    private Object farmersBridge, typedAccess, fluidService;
    private Class<?> bridgeType;
    private org.bukkit.entity.Player platform;
    private Player actor;
    private PlayerInventory playerInventory;
    private boolean baseline, ticketPrepared, forceLoadedBefore;
    private BlockDefinition tankDefinition;
    private Object tankController, tankInventory;
    private SchedulerTask timeout;

    @Override public void onEnable() {
        String name = System.getProperty("cookery.fluid.world", "missing-acceptance-world");
        world = Bukkit.getWorld(name);
        if (world == null) { finish(new IllegalStateException("Acceptance world is unavailable: " + name)); return; }
        owner = new Location(world, 8, 180, 8);
        tankLocation = new Location(world, 11, 180, 8);
        FoliaUtil.runLater(() -> guarded(this::start), 100L, owner);
    }

    private void start() throws Exception {
        check(Boolean.getBoolean("cookery.fluid.acceptance"), "explicit dedicated fluid-compatibility acceptance flag");
        check(!world.getName().equals("world") && Bukkit.getOnlinePlayers().isEmpty(), "isolated world has no online users");
        check(owns(), "fixture starts on the actual owning server scheduler");
        check(version("CraftEngine").equals(System.getProperty("cookery.fluid.craftengine")), "exact CraftEngine runtime version");
        check(Bukkit.getPluginManager().isPluginEnabled("KaleidoscopeCookeryPlugin"), "real KC plugin is enabled");
        farmers = Bukkit.getPluginManager().getPlugin("Farmersdelight-Plugin-Pro");
        if (farmers == null) farmers = Bukkit.getPluginManager().getPlugin("FarmersDelight");
        fluidCore = Bukkit.getPluginManager().getPlugin("FluidCore");
        check(farmers != null && farmers.isEnabled() && fluidCore != null && fluidCore.isEnabled(), "actual FD and FluidCore plugins are enabled");
        baseline = System.getProperty("cookery.fluid.mode", "candidate").equals("baseline");
        bridgeType = Class.forName("com.huidu.farmersdelight.fluid.FluidCoreBridge", true, farmers.getClass().getClassLoader());
        farmersBridge = call(call(farmers, "getFluidRecipes"), "bridge");
        check((boolean) call(farmersBridge, "available"), "FD typed FluidCore service binding is available");
        typedAccess = field(farmersBridge, "api");
        Class<?> serviceType = Class.forName("com.ydxc20091.fluidcore.BukkitFluidCoreService", true, fluidCore.getClass().getClassLoader());
        fluidService = Bukkit.getServicesManager().load(serviceType);
        check(fluidService != null, "actual FluidCore public service is registered");
        check(Arrays.stream(CustomBlockAttemptPlaceEvent.getHandlerList().getRegisteredListeners())
                .anyMatch(listener -> listener.getPlugin() == farmers
                        && listener.getListener().getClass().getName().endsWith("FluidRecipeListener")),
                "actual FD fluid listener is registered on CE attempt-place events");
        check(CraftEngineBlocks.byId(TEAPOT) != null && CraftEngineBlocks.byId(STOVE) != null, "original KC teapot and stove definitions are loaded");
        check(BuiltInRegistries.BLOCK_BEHAVIOR_TYPE.getValue(Key.of("farmersdelight:jug")) != null,
                "FD native jug alias factory is registered independently of block namespaces");
        timeout = FoliaUtil.runLater(() -> finish(new TimeoutException("Teapot placement verification exceeded bounded time")), 1200L, owner);
        global(() -> {
            forceLoadedBefore = world.isChunkForceLoaded(0, 0);
            ticketPrepared = true;
            world.setChunkForceLoaded(0, 0, true);
        }).whenComplete((ignored, error) -> {
            if (error != null) finish(error);
            else FoliaUtil.run(() -> guarded(this::verify), owner);
        });
    }

    private void verify() throws Exception {
        check(owns(), "all item, event, block and controller operations use the owner thread");
        storage = BukkitWorldManager.instance().getWorld(world.getUID()).storageWorld();
        check(storage != null, "actual CE storage world is available");
        for (Location location : List.of(owner.clone().add(0, -1, 0), owner, owner.clone().add(0, 1, 0),
                tankLocation.clone().add(0, -1, 0), tankLocation, tankLocation.clone().add(0, 1, 0))) {
            Block block = location.getBlock();
            var state = storage.getBlockStateAtIfLoaded(pos(location));
            check(!CraftEngineBlocks.isCustomBlock(block) && (state == null || state.isEmpty())
                    && storage.getBlockEntityAtIfLoaded(pos(location), false) == null && !(block.getState() instanceof TileState),
                    "reserved fixture cell contains no unknown custom state or tile entity at " + pos(location));
            originals.put(block, block.getBlockData().clone());
        }
        createActors();
        for (String surface : List.of("ordinary", "unlit_stove", "lit_stove")) {
            prepareSurface(surface);
            for (String payload : List.of("water", "lava", "finished_3_portions"))
                for (InteractionHand hand : List.of(InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND))
                    teapotCase(surface, payload, hand);
        }
        if (!baseline) {
            wrongNativePlacementCases();
            prepareRealTank();
            foreignTankPlacementCases();
            conversionCases();
        }
        finish(null);
    }

    private void prepareSurface(String surface) {
        removeOwned(owner.getBlock());
        Block base = owner.clone().add(0, -1, 0).getBlock();
        removeOwned(base);
        owner.getBlock().setType(Material.AIR, false);
        owner.clone().add(0, 1, 0).getBlock().setType(Material.AIR, false);
        if (surface.equals("ordinary")) base.setType(Material.STONE, false);
        else {
            ImmutableBlockState stove = CraftEngineBlocks.byId(STOVE).defaultState();
            StoveBehavior behavior = stove.behavior().getFirst(StoveBehavior.class);
            check(behavior != null, "real KC stove behavior is available for " + surface);
            stove = stove.with(behavior.getLitProperty(), surface.equals("lit_stove"));
            check(CraftEngineBlocks.place(base.getLocation(), stove, false), "real KC " + surface + " is placed beneath the fixture");
            ownedBlocks.put(base, STOVE);
        }
    }

    private Item teapot(String kind) {
        Item item = InventoryUtils.createOrEmpty(TEAPOT);
        check(!item.isEmpty(), "real CE builds KC teapot " + kind);
        CompoundTag data = new CompoundTag();
        data.putString("fluid", kind.equals("lava") ? "minecraft:lava" : "minecraft:water");
        if (kind.equals("finished_3_portions")) {
            Item result = InventoryUtils.createOrEmpty(Key.of("minecraft:baked_potato"));
            CompoundTag marker = new CompoundTag();
            marker.putString("literal", "saved-original-tea-result");
            marker.putInt("quality", 7);
            marker.putByteArray("opaque", new byte[]{3, 1, 4, 1, 5});
            result.setSparrowTag(marker, "teapotprobe:original_result");
            data.putInt("status", TeapotController.FINISHED);
            data.putInt("servings", 3);
            BlockEntityNbt.putItem(data, "result", result);
        }
        item.setSparrowTag(data, TeapotBar.ITEM_DATA_KEY);
        return item;
    }

    private void teapotCase(String surface, String kind, InteractionHand hand) throws Exception {
        String label = surface + "/" + kind + "/" + hand;
        Item held = teapot(kind);
        CompoundTag expected = ((CompoundTag) held.getSparrowTag(TeapotBar.ITEM_DATA_KEY)).deepClone();
        clearInventory();
        put(hand, ItemStackUtils.getBukkitStack(held).clone());
        ItemStack before = held(hand).clone();
        check((boolean) staticCall(bridgeType, "hasForeignFluidData", before), "old global FD placement predicate misclassifies native KC data for " + label);
        check((boolean) staticCall(bridgeType, "hasProtectedRecipeData", before), "actual conversion guard protects filled KC data for " + label);
        CustomBlockAttemptPlaceEvent event = attempt(CraftEngineBlocks.byId(TEAPOT).defaultState(), hand, owner);
        check(event.isCancelled() == baseline, (baseline ? "baseline reproduces global cancellation for " : "candidate permits native KC attempt for ") + label);
        check(before.equals(held(hand)), "registered attempt listeners preserve the complete original held item for " + label);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("surface", surface); row.put("payload", kind); row.put("hand", hand.name());
        row.put("attempt_cancelled", event.isCancelled()); row.put("full_player_place_path_tested", false);
        if (!baseline) {
            put(hand, null); // This is the declared fixture's hand bookkeeping, not a test of CE's NMS shrink path.
            Item pickup = restoreAndPickUp(held, expected, label + "/first");
            clearInventory();
            Item second = restoreAndPickUp(pickup, expected, label + "/second");
            check(((CompoundTag) second.getSparrowTag(TeapotBar.ITEM_DATA_KEY)).equals(expected), "second real placement/pickup retains complete native tea payload for " + label);
            row.put("real_controller_round_trips", 2);
            row.put("fluid_result_servings_preserved", true);
        }
        cases.add(row);
    }

    private Item restoreAndPickUp(Item carried, CompoundTag expected, String label) {
        check(CraftEngineBlocks.place(owner, CraftEngineBlocks.byId(TEAPOT).defaultState(), false), "raw CE places an actual teapot block for " + label);
        ownedBlocks.put(owner.getBlock(), TEAPOT);
        BlockEntity entity = storage.getBlockEntityAtIfLoaded(pos(owner), true);
        check(entity != null, "actual placed teapot block entity exists for " + label);
        TeapotController controller = entity.controller.get(TeapotController.class, 0);
        check(controller != null, "actual placed KC teapot controller exists for " + label);
        controller.loadCustomDataFromItem(carried.copy());
        CompoundTag saved = new CompoundTag();
        controller.saveCustomData(saved);
        CompoundTag data = saved.getCompound("kaleidoscopecookery:teapot");
        check(expected.getString("fluid").equals(data.getString("fluid")), "placement restores native liquid for " + label);
        if (expected.getInt("status", 0) == TeapotController.FINISHED) {
            check(data.getInt("status", 0) == TeapotController.FINISHED && data.getInt("servings", 0) == 3,
                    "placement restores finished state and three portions for " + label);
            check(expected.get("result").equals(data.get("result")), "placement restores complete saved result NBT without reroll for " + label);
        }
        clearInventory();
        check(controller.takeTeapot(actor, InteractionHand.MAIN_HAND), "actual controller pickup succeeds for " + label);
        check(!CraftEngineBlocks.isCustomBlock(owner.getBlock()), "actual pickup removes its CE block for " + label);
        ownedBlocks.remove(owner.getBlock());
        Item pickup = BukkitItemManager.instance().wrap(held(InteractionHand.MAIN_HAND));
        check(!pickup.isEmpty() && pickup.id().equals(TEAPOT), "actual controller returns the KC teapot item for " + label);
        check(expected.equals(pickup.getSparrowTag(TeapotBar.ITEM_DATA_KEY)), "actual pickup preserves complete native payload for " + label);
        return pickup.copy();
    }

    private void wrongNativePlacementCases() {
        for (String marker : List.of("container_data", "container_initialized", "tank_data"))
            for (InteractionHand hand : List.of(InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND)) {
                ItemStack item = ItemStackUtils.getBukkitStack(teapot("water")).clone();
                withWrongMarker(item, marker);
                clearInventory(); put(hand, item);
                ItemStack before = item.clone();
                CustomBlockAttemptPlaceEvent event = attempt(CraftEngineBlocks.byId(TEAPOT).defaultState(), hand, owner);
                check(event.isCancelled(), "candidate rejects native FluidCore wrong-type " + marker + " on KC target in " + hand);
                check(before.equals(held(hand)) && !CraftEngineBlocks.isCustomBlock(owner.getBlock()), "rejected native marker does not consume or place the KC item: " + marker + "/" + hand);
            }
    }

    private void prepareRealTank() throws Exception {
        String requested = System.getProperty("cookery.fluid.tank");
        if (requested != null && !requested.isBlank()) tankDefinition = CraftEngineBlocks.byId(Key.of(requested));
        else for (BlockDefinition definition : CraftEngineBlocks.loadedBlocks().values())
            if ((boolean) call(farmersBridge, "isNativeTankState", definition.defaultState())) { tankDefinition = definition; break; }
        check(tankDefinition != null && (boolean) call(farmersBridge, "isNativeTankState", tankDefinition.defaultState()), "actual native tank definition is identified by behavior including aliases");
        check(!(boolean) call(farmersBridge, "isNativeTankState", CraftEngineBlocks.byId(TEAPOT).defaultState()), "native target detection excludes KC teapot");
        tankLocation.clone().add(0, -1, 0).getBlock().setType(Material.STONE, false);
        tankLocation.clone().add(0, 1, 0).getBlock().setType(Material.AIR, false);
        tankLocation.getBlock().setType(Material.AIR, false);
        check(CraftEngineBlocks.place(tankLocation, tankDefinition.defaultState(), false), "actual FluidCore tank is placed for conversion safety validation");
        ownedBlocks.put(tankLocation.getBlock(), tankDefinition.id());
        storage.getBlockEntityAtIfLoaded(pos(tankLocation), true);
        Object optional = call(call(call(fluidService, "bridge"), "resolver"), "controller", tankLocation);
        tankController = ((java.util.Optional<?>) optional).orElse(null);
        check(tankController != null, "actual FluidCore resolves the placed native tank controller");
        tankInventory = call(tankController, "inventory");
    }

    private void foreignTankPlacementCases() throws Exception {
        Item nativeItem = InventoryUtils.createOrEmpty(tankDefinition.id());
        check(!nativeItem.isEmpty(), "actual native tank item exists with its matching block definition");
        CompoundTag opaque = new CompoundTag(); opaque.putString("original", "opaque-foreign-tank-data");
        nativeItem.setSparrowTag(opaque, "libuid:saved_jug");
        for (InteractionHand hand : List.of(InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND)) {
            clearInventory(); put(hand, ItemStackUtils.getBukkitStack(nativeItem).clone());
            ItemStack before = held(hand).clone();
            CustomBlockAttemptPlaceEvent event = attempt(tankDefinition.defaultState(), hand, owner);
            check(event.isCancelled(), "same-ID native tank with opaque foreign data remains blocked in " + hand);
            check(before.equals(held(hand)), "native tank rejection retains its complete foreign payload in " + hand);
        }
    }

    private void conversionCases() throws Exception {
        Object initialContent = call(call(tankController, "storage"), "content", 0);
        for (String kind : List.of("water", "lava", "finished_3_portions")) {
            ItemStack input = ItemStackUtils.getBukkitStack(teapot(kind)).clone();
            call(tankInventory, "load", input, null);
            Object outcome = call(typedAccess, "processGenericTank", tankController, false, false);
            check(outcome.toString().equals("PROTECTED_DATA"), "actual FD tank conversion rejects native KC " + kind);
            check(input.equals(call(tankInventory, "input")) && call(tankInventory, "output") == null
                    && initialContent.equals(call(call(tankController, "storage"), "content", 0)),
                    "actual conversion retains KC input, output slot and tank fluid for " + kind);
        }
        for (String marker : List.of("container_data", "container_initialized", "tank_data")) {
            ItemStack input = ItemStackUtils.getBukkitStack(InventoryUtils.createOrEmpty(tankDefinition.id())).clone();
            withWrongMarker(input, marker);
            ItemStack before = input.clone();
            Object itemData = call(fluidService, "itemData");
            Object read = call(itemData, "read", input);
            check((boolean) call(read, "protectedData"), "actual FluidCore decoder protects wrong-type native record " + marker);
            Class<?> stackType = Class.forName("com.ydxc20091.fluidcore.api.FluidStack", true, fluidCore.getClass().getClassLoader());
            Object empty = stackType.getField("EMPTY").get(null);
            boolean rejected = false;
            try { call(itemData, "write", input, empty); }
            catch (Exception error) { rejected = error.getClass().getSimpleName().equals("ProtectedDataException"); }
            check(rejected && before.equals(input), "actual FluidCore refuses to overwrite protected record without consuming it: " + marker);
            call(tankInventory, "load", input, null);
            // Emptying uses the real tank's available capacity, so a zero-fill short circuit
            // cannot hide whether the native container's malformed record is protected.
            Object outcome = call(typedAccess, "processGenericTank", tankController, true, false);
            check(outcome.toString().equals("PROTECTED_DATA") && before.equals(call(tankInventory, "input"))
                    && initialContent.equals(call(call(tankController, "storage"), "content", 0)),
                    "actual FD conversion cannot consume protected native FluidCore data: " + marker + "; outcome=" + outcome);
        }
        call(tankInventory, "load", null, null);
    }

    private CustomBlockAttemptPlaceEvent attempt(ImmutableBlockState state, InteractionHand hand, Location location) {
        check(owns(), "CE attempt event is dispatched on its actual owning scheduler");
        CustomBlockAttemptPlaceEvent event = new CustomBlockAttemptPlaceEvent(platform, location.clone(), state,
                BlockFace.UP, location.clone().add(0, -1, 0).getBlock(), hand);
        Bukkit.getPluginManager().callEvent(event);
        RuntimeException unsupported = proxyFailure.getAndSet(null);
        if (unsupported != null) throw new IllegalStateException("A real event listener required an unsupported simulated-player operation", unsupported);
        return event;
    }
    private static void withWrongMarker(ItemStack item, String marker) {
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("fluidcore", marker), PersistentDataType.STRING, "wrong-native-type-preserve-me");
        item.setItemMeta(meta);
    }
    private void createActors() {
        UUID uuid = UUID.nameUUIDFromBytes("TeapotPlacementProbe".getBytes(StandardCharsets.UTF_8));
        playerInventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(), new Class<?>[]{PlayerInventory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getItemInMainHand" -> inventory[0] == null ? new ItemStack(Material.AIR) : inventory[0];
                    case "getItemInOffHand" -> inventory[40] == null ? new ItemStack(Material.AIR) : inventory[40];
                    case "setItemInMainHand" -> { inventory[0] = (ItemStack) args[0]; yield null; }
                    case "setItemInOffHand" -> { inventory[40] = (ItemStack) args[0]; yield null; }
                    case "getItem" -> inventory[(int) args[0]];
                    case "setItem" -> { inventory[(int) args[0]] = (ItemStack) args[1]; yield null; }
                    case "getStorageContents" -> Arrays.copyOf(inventory, 36);
                    case "getContents" -> inventory.clone();
                    case "getHeldItemSlot" -> 0;
                    case "getSize" -> 41;
                    case "getMaxStackSize" -> 64;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> throw unsupported("Unexpected fixture inventory call " + method.getName());
                });
        platform = (org.bukkit.entity.Player) Proxy.newProxyInstance(org.bukkit.entity.Player.class.getClassLoader(), new Class<?>[]{org.bukkit.entity.Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> "TeapotPlacementProbe";
                    case "getInventory" -> playerInventory;
                    case "getWorld" -> world;
                    case "getLocation", "getEyeLocation" -> owner.clone().add(0.5, 0, 0.5);
                    case "getGameMode" -> GameMode.SURVIVAL;
                    case "isOnline", "isValid", "hasPermission", "isOp" -> true;
                    case "isDead", "isSneaking" -> false;
                    case "getLocale" -> "zh_cn";
                    case "locale" -> Locale.SIMPLIFIED_CHINESE;
                    case "getTicksLived" -> 100;
                    case "getServer" -> Bukkit.getServer();
                    case "sendMessage", "sendActionBar" -> { messages.add(Arrays.toString(args)); yield null; }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    case "toString" -> "SimulatedTeapotPlacementPlayer";
                    default -> throw unsupported("Unexpected simulated Bukkit player call " + method.getName());
                });
        actor = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "platformPlayer" -> platform;
                    case "getItemInHand" -> held((InteractionHand) args[0]) == null ? Item.empty()
                            : BukkitItemManager.instance().wrap(held((InteractionHand) args[0]));
                    case "setItemInHand" -> { put((InteractionHand) args[0], ItemStackUtils.getBukkitStack((Item) args[1]).clone()); yield null; }
                    case "canInstabuild", "isCreativeMode" -> false;
                    case "sendActionBar", "sendPacket", "swingHand" -> null;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    case "toString" -> "SimulatedTeapotPlacementCEActor";
                    default -> throw unsupported("Unexpected simulated CE actor call " + method.getName());
                });
    }
    private ItemStack held(InteractionHand hand) { return inventory[hand == InteractionHand.MAIN_HAND ? 0 : 40]; }
    private void put(InteractionHand hand, ItemStack item) { inventory[hand == InteractionHand.MAIN_HAND ? 0 : 40] = item; }
    private void clearInventory() { Arrays.fill(inventory, null); }
    private RuntimeException unsupported(String message) {
        RuntimeException error = new UnsupportedOperationException(message);
        proxyFailure.compareAndSet(null, error);
        return error;
    }
    private boolean owns() { return owner != null && Bukkit.isOwnedByCurrentRegion(world, 0, 0); }
    private static BlockPos pos(Location location) { return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()); }
    private void removeOwned(Block block) {
        Key expected = ownedBlocks.get(block);
        if (expected == null) return;
        ImmutableBlockState current = CraftEngineBlocks.getCustomBlockState(block);
        if (current != null && !current.isEmpty()) {
            if (!current.owner().value().id().equals(expected)) throw new IllegalStateException("Fixture block was replaced by unknown custom data: " + block.getLocation());
            BlockEntity entity = storage.getBlockEntityAtIfLoaded(new BlockPos(block.getX(), block.getY(), block.getZ()), false);
            if (entity != null) {
                TeapotController teapot = entity.controller.get(TeapotController.class, 0);
                if (teapot != null) {
                    try { setField(teapot, "pickedUp", true); }
                    catch (Exception error) { throw new IllegalStateException("Cannot safely suppress fixture teapot drops", error); }
                }
            }
            CraftEngineBlocks.remove(block, false);
        }
        ownedBlocks.remove(block);
    }
    private CompletableFuture<Void> global(CheckedAction action) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try { CraftEngine.instance().scheduler().platform().run(() -> {
            try { action.run(); future.complete(null); } catch (Throwable error) { future.completeExceptionally(error); }
        }); } catch (Throwable error) { future.completeExceptionally(error); }
        return future;
    }
    private void guarded(CheckedAction action) {
        if (finished.get()) return;
        try { action.run(); } catch (Throwable error) { finish(error); }
    }
    private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks.add(message); }
    private void finish(Throwable error) {
        if (!finished.compareAndSet(false, true)) return;
        if (timeout != null) timeout.cancel();
        if (owner == null) { writeReport(error, false); return; }
        FoliaUtil.run(() -> {
            Throwable problem = error;
            boolean cleaned = true;
            try {
                if (tankInventory != null) call(tankInventory, "load", null, null);
                for (Block block : new ArrayList<>(ownedBlocks.keySet())) removeOwned(block);
                for (var entry : originals.entrySet()) {
                    if (CraftEngineBlocks.isCustomBlock(entry.getKey())) throw new IllegalStateException("Cannot restore over unknown custom block");
                    entry.getKey().setBlockData(entry.getValue(), false);
                }
                clearInventory();
            } catch (Throwable cleanupError) { cleaned = false; if (problem == null) problem = cleanupError; else problem.addSuppressed(cleanupError); }
            Throwable finalProblem = problem; boolean blocksCleaned = cleaned;
            global(() -> { if (ticketPrepared) world.setChunkForceLoaded(0, 0, forceLoadedBefore); }).whenComplete((ignored, ticketError) -> {
                Throwable finalError = finalProblem;
                if (ticketError != null) { if (finalError == null) finalError = ticketError; else finalError.addSuppressed(ticketError); }
                writeReport(finalError, blocksCleaned && ticketError == null);
            });
        }, owner);
    }
    private void writeReport(Throwable error, boolean cleanup) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("passed", error == null && cleanup);
        report.put("mode", baseline ? "baseline" : "candidate");
        report.put("server", Bukkit.getVersion()); report.put("craftengine", version("CraftEngine"));
        report.put("cookery", version("KaleidoscopeCookeryPlugin")); report.put("farmers", farmers == null ? null : farmers.getDescription().getVersion());
        report.put("fluidcore", version("FluidCore")); report.put("folia", FoliaUtil.isFolia());
        report.put("world", world == null ? null : world.getName());
        report.put("scope", "Actual registered CE attempt event dispatch with simulated Bukkit player; real CE block creation and KC controller restore/pickup; actual FluidCore native decoder and FD generic tank conversion adapter; full NMS/player placement path and graphical client are outside this report");
        report.put("players_simulated", true); report.put("full_player_placement_tested", false);
        report.put("native_tank_id", tankDefinition == null ? null : tankDefinition.id().asString());
        report.put("cleanup_completed_before_report", cleanup); report.put("check_count", checks.size());
        report.put("checks", List.copyOf(checks)); report.put("cases", List.copyOf(cases)); report.put("messages", List.copyOf(messages));
        report.put("error", error == null ? null : error.toString());
        if (error != null) getLogger().log(java.util.logging.Level.SEVERE, "TEAPOT_PLACEMENT_PROBE_FAIL", error);
        else getLogger().info("TEAPOT_PLACEMENT_PROBE_PASS " + checks.size() + " checks");
        String output = json(report);
        CraftEngine.instance().scheduler().executeAsync(() -> {
            try { Files.createDirectories(getDataFolder().toPath()); Files.writeString(getDataFolder().toPath().resolve("result.json"), output, StandardCharsets.UTF_8); }
            catch (Exception failure) { getLogger().log(java.util.logging.Level.SEVERE, "Cannot write teapot acceptance report", failure); }
        });
    }
    private static String version(String name) { Plugin plugin = Bukkit.getPluginManager().getPlugin(name); return plugin == null ? "missing" : plugin.getDescription().getVersion(); }
    private static Object field(Object target, String name) throws Exception { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static void setField(Object target, String name, Object value) throws Exception { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
    private static Object staticCall(Class<?> type, String name, Object... args) throws Exception { return invoke(type, null, name, args); }
    private static Object call(Object target, String name, Object... args) throws Exception { return invoke(target.getClass(), target, name, args); }
    private static Object invoke(Class<?> type, Object target, String name, Object[] args) throws Exception {
        // Public service methods may be supplied by interface defaults, not the plugin class itself.
        List<Method> methods = new ArrayList<>(Arrays.asList(type.getMethods()));
        for (Class<?> owner = type; owner != null; owner = owner.getSuperclass())
            methods.addAll(Arrays.asList(owner.getDeclaredMethods()));
        for (Method method : methods) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] parameters = method.getParameterTypes(); boolean accepts = true;
            for (int i = 0; i < args.length; i++) if (args[i] != null && !boxed(parameters[i]).isInstance(args[i])) { accepts = false; break; }
            if (!accepts) continue;
            method.setAccessible(true);
            try { return method.invoke(target, args); }
            catch (InvocationTargetException error) { if (error.getCause() instanceof Exception exception) throw exception; if (error.getCause() instanceof Error failure) throw failure; throw error; }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
    private static Class<?> boxed(Class<?> type) {
        if (type == int.class) return Integer.class; if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class; return type;
    }
    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
    private static String json(Object value) {
        if (value == null) return "null"; if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) return map.entrySet().stream().map(entry -> quote(entry.getKey().toString()) + ":" + json(entry.getValue()))
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        if (value instanceof Iterable<?> entries) { List<String> parts = new ArrayList<>(); entries.forEach(item -> parts.add(json(item))); return "[" + String.join(",", parts) + "]"; }
        return quote(value.toString());
    }
    private static String quote(String value) { return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + '"'; }
}
