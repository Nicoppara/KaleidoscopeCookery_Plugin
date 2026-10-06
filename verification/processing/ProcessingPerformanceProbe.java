import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.world.BukkitWorldManager;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.kaleidoscope.cookery.util.FoliaUtil;
import org.bukkit.Location;

/** Same detached-controller workload for old and candidate JARs, without a link to any new plan class.
 * It measures owner-thread tick CPU/allocation, not scheduler latency, client packets or online TPS.
 */
public final class ProcessingPerformanceProbe {
    private static final int IDLE_COUNT = 1000;
    private static final int ACTIVE_COUNT = 200;
    private static final int WARMUP = 500;
    private static final int SAMPLES = 400;
    private static final int WORK = 1_000_000;
    private static final String PREFIX = "net.kaleidoscope.cookery.";
    private static final Key INPUT = Key.of("minecraft:nether_star");
    private static final String[] IDLE = {"pot", "stockpot", "steamer", "teapot", "shawarma_spit"};
    private static final String[] ACTIVE = {"stockpot", "steamer", "teapot", "shawarma_spit"};
    private static final Map<String, String> CLASS_NAMES = Map.of("pot", "PotController", "stockpot", "StockpotController",
            "steamer", "SteamerController", "teapot", "TeapotController", "shawarma_spit", "ShawarmaSpitController");

    private record Tick(Object target, MethodHandle tick) {
        void run() throws Throwable { tick.invokeExact(target); }
    }
    private record ActiveDevice(String type, Object controller, int initialProgress) {}

    public static void verify(JavaPlugin plugin, World world, List<String> checks) throws Exception {
        measure(plugin, world, checks);
    }

    public static Map<String, Object> measure(JavaPlugin plugin, World world, List<String> checks) throws Exception {
        if (!Bukkit.isOwnedByCurrentRegion(world, 0, 0) || !world.isChunkLoaded(0, 0))
            throw new AssertionError("Performance workload requires its loaded chunk owner");
        Map<BlockPos, BlockData> physical = new LinkedHashMap<>();
        List<BlockEntityController> controllers = new ArrayList<>(IDLE_COUNT + ACTIVE_COUNT);
        List<ActiveDevice> activeDevices = new ArrayList<>(ACTIVE_COUNT);
        try {
            for (int x = 1; x <= 14; x++) for (int z = 1; z <= 14; z++) {
                remember(world, physical, x, 99, z, Material.MAGMA_BLOCK);
                remember(world, physical, x, 101, z, Material.AIR);
            }
            CEWorld storage = BukkitWorldManager.instance().getWorld(world.getUID()).storageWorld();
            Map<String, Class<?>> types = new HashMap<>();
            Map<String, MethodHandle> methods = new HashMap<>();
            for (String name : IDLE) {
                Class<?> type = Class.forName(PREFIX + "block.entity." + CLASS_NAMES.get(name));
                types.put(name, type);
                Method method = type.getDeclaredMethod("tick"); method.setAccessible(true);
                methods.put(name, MethodHandles.lookup().unreflect(method).asType(MethodType.methodType(void.class, Object.class)));
            }
            Tick[] ticks = new Tick[IDLE_COUNT + ACTIVE_COUNT];
            for (int i = 0; i < ticks.length; i++) {
                boolean active = i >= IDLE_COUNT;
                String name = active ? ACTIVE[(i - IDLE_COUNT) % ACTIVE.length] : IDLE[i % IDLE.length];
                int x = 1 + (i % 14);
                int z = 1 + ((i / 14) % 14);
                ImmutableBlockState state = CraftEngineBlocks.byId(Key.of("kaleidoscopecookery:" + name)).defaultState();
                if (active && name.equals("stockpot")) state = flag(state, "has_lid", true);
                if (active && name.equals("shawarma_spit")) state = flag(state, "powered", true);
                BlockEntity entity = new BlockEntity(new BlockPos(x, 100, z), state); entity.setWorld(storage);
                BlockEntityController controller = entity.controller.getAt(types.get(name).asSubclass(BlockEntityController.class), 0);
                if (controller == null) throw new AssertionError("Missing real " + name + " controller");
                controllers.add(controller);
                if (active) {
                    active(controller, name);
                    activeDevices.add(new ActiveDevice(name, controller, progress(controller, name)));
                }
                ticks[i] = new Tick(controller, methods.get(name));
            }
            plugin.getLogger().info("PERFORMANCE_WARMUP " + WARMUP + " frames, 1000 idle + 200 active detached controllers");
            for (int iteration = 0; iteration < WARMUP; iteration++) frame(ticks);
            int[] measuredBefore = new int[activeDevices.size()];
            for (int i = 0; i < activeDevices.size(); i++) {
                ActiveDevice device = activeDevices.get(i);
                measuredBefore[i] = progress(device.controller(), device.type());
                if (measuredBefore[i] <= device.initialProgress())
                    throw new AssertionError("Active controller did not progress during warmup: " + device.type() + " #" + i);
            }
            com.sun.management.ThreadMXBean allocation = ManagementFactory.getPlatformMXBean(com.sun.management.ThreadMXBean.class);
            boolean allocationsAvailable = allocation != null && allocation.isThreadAllocatedMemorySupported();
            if (allocationsAvailable && !allocation.isThreadAllocatedMemoryEnabled()) allocation.setThreadAllocatedMemoryEnabled(true);
            long threadId = Thread.currentThread().threadId();
            long[] durations = new long[SAMPLES];
            Map<String, Integer> pendingTasksBefore = pendingTasks(plugin);
            long bytesBefore = allocationsAvailable ? allocation.getThreadAllocatedBytes(threadId) : -1;
            long cpuBefore = ManagementFactory.getThreadMXBean().isCurrentThreadCpuTimeSupported()
                    ? ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() : -1;
            long started = System.nanoTime();
            for (int iteration = 0; iteration < SAMPLES; iteration++) {
                long before = System.nanoTime(); frame(ticks); durations[iteration] = System.nanoTime() - before;
            }
            long wall = System.nanoTime() - started;
            long bytes = allocationsAvailable ? allocation.getThreadAllocatedBytes(threadId) - bytesBefore : -1;
            long cpu = cpuBefore >= 0 ? ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - cpuBefore : -1;
            Map<String, Integer> pendingTasksAfter = pendingTasks(plugin);
            Map<String, Integer> minimumProgress = new LinkedHashMap<>();
            Map<String, Integer> maximumProgress = new LinkedHashMap<>();
            for (int i = 0; i < activeDevices.size(); i++) {
                ActiveDevice device = activeDevices.get(i);
                int delta = progress(device.controller(), device.type()) - measuredBefore[i];
                if (delta <= 0) throw new AssertionError("Active controller stalled during measured frames: " + device.type() + " #" + i);
                minimumProgress.merge(device.type(), delta, Math::min);
                maximumProgress.merge(device.type(), delta, Math::max);
            }
            long[] sorted = durations.clone(); Arrays.sort(sorted);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("measurement", "synchronous detached-controller tick microbenchmark; not live server TPS");
            result.put("idle_controllers", IDLE_COUNT); result.put("active_controllers", ACTIVE_COUNT);
            result.put("idle_types", List.of(IDLE)); result.put("active_types", List.of(ACTIVE));
            result.put("active_per_type", ACTIVE_COUNT / ACTIVE.length);
            result.put("active_progress_validated", activeDevices.size());
            result.put("measured_progress_min_by_type", minimumProgress);
            result.put("measured_progress_max_by_type", maximumProgress);
            result.put("warmup_frames", WARMUP); result.put("sample_frames", SAMPLES);
            result.put("wall_nanos", wall); result.put("thread_cpu_nanos", cpu < 0 ? null : cpu);
            result.put("mean_frame_nanos", Arrays.stream(durations).average().orElseThrow());
            result.put("median_frame_nanos", percentile(sorted, 0.50));
            result.put("p95_frame_nanos", percentile(sorted, 0.95)); result.put("p99_frame_nanos", percentile(sorted, 0.99));
            result.put("thread_allocated_bytes", bytes < 0 ? null : bytes);
            result.put("allocated_bytes_per_frame", bytes < 0 ? null : bytes / (double) SAMPLES);
            result.put("nano_samples", Arrays.stream(durations).boxed().toList());
            result.put("pending_bukkit_tasks_before_by_owner", pendingTasksBefore);
            result.put("pending_bukkit_tasks_after_by_owner", pendingTasksAfter);
            result.put("task_measurement_scope", "BukkitScheduler.getPendingTasks for CE/Cookery/probe owners only; excludes internal CE tickers and Folia region/entity schedulers");
            result.put("packet_count", null);
            result.put("java", System.getProperty("java.version"));
            result.put("cookery_version", Bukkit.getPluginManager().getPlugin("KaleidoscopeCookeryPlugin").getPluginMeta().getVersion());
            result.put("craftengine_version", Bukkit.getPluginManager().getPlugin("CraftEngine").getPluginMeta().getVersion());
            result.put("minecraft", Bukkit.getMinecraftVersion());
            result.put("world_game_time", world.getGameTime());
            result.put("limitations", List.of("world gameTime remains fixed during each synchronous run; controller positions distribute phases",
                    "no recipe-start or completion is included; batches have the same 1000000-tick remaining work",
                    "no online clients; packets, internal CE ticker entries, Folia scheduler tasks and off-thread allocations are not instrumented",
                    "no player/animal millstone movement workload and no Folia cross-region load is measured",
                    "warmup and construction allocations excluded; GC and host scheduling can affect wall-time tails"));
            checks.add("performance: completed identical 1000-idle/200-active workload for " + SAMPLES + " sample frames");
            plugin.getLogger().info("PERFORMANCE_RESULT median=" + result.get("median_frame_nanos")
                    + "ns p95=" + result.get("p95_frame_nanos") + "ns p99=" + result.get("p99_frame_nanos")
                    + "ns allocated/frame=" + result.get("allocated_bytes_per_frame"));
            return result;
        } finally {
            for (BlockEntityController controller : controllers) {
                controller.gatherElements(element -> element.deactivate());
            }
            physical.forEach((position, data) -> world.getBlockAt(position.x(), position.y(), position.z()).setBlockData(data, false));
        }
    }

    /** Comparable baseline/candidate measurements: the real world clock advances between sampled frames. */
    public static CompletableFuture<Map<String, Object>> measureAsync(JavaPlugin plugin, World world, List<String> checks) {
        AsyncSamples samples = new AsyncSamples(plugin, world, checks);
        samples.schedule(samples::setup);
        return samples.result;
    }

    private static final class AsyncSamples {
        private static final int LIVE_WARMUP = 100, LIVE_SAMPLES = 100, BATCH = 50;
        final JavaPlugin plugin;
        final World world;
        final List<String> checks;
        final CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
        final Map<BlockPos, BlockData> physical = new LinkedHashMap<>();
        final List<BlockEntityController> controllers = new ArrayList<>();
        final List<ActiveDevice> activeDevices = new ArrayList<>();
        final Map<String, Class<?>> types = new HashMap<>();
        final Map<String, MethodHandle> methods = new HashMap<>();
        final Tick[] ticks = new Tick[IDLE_COUNT + ACTIVE_COUNT];
        final long[] durations = new long[LIVE_SAMPLES], clocks = new long[LIVE_SAMPLES];
        final int[] measuredBefore = new int[ACTIVE_COUNT];
        final List<Map.Entry<BlockPos, BlockData>> restoration = new ArrayList<>();
        CEWorld storage;
        com.sun.management.ThreadMXBean allocation;
        java.lang.management.ThreadMXBean cpuBean;
        Map<String, Integer> tasksBefore;
        Map<String, Object> completedReport;
        Throwable failure;
        int physicalRow = 1, prepared, warmup, sampled, cleanupController, cleanupBlock, duplicateClockCallbacks;
        long lastClock = Long.MIN_VALUE, elapsedStart, allocatedBytes, cpuNanos;
        boolean allocationAvailable, cpuAvailable, finishing;

        AsyncSamples(JavaPlugin plugin, World world, List<String> checks) {
            this.plugin = plugin; this.world = world; this.checks = checks;
        }

        void schedule(Runnable action) {
            FoliaUtil.runLater(action, 1L, new Location(world, 8, 100, 8));
        }

        void owner() {
            if (!Bukkit.isOwnedByCurrentRegion(world, 0, 0) || !world.isChunkLoaded(0, 0))
                throw new AssertionError("Live performance operation is outside its loaded owner region");
        }

        void setup() {
            try {
                owner();
                if (!Bukkit.getOnlinePlayers().isEmpty()) throw new AssertionError("Players joined the isolated performance profile");
                // Restore physical data even if setup fails; work is split over ticks rather than pausing them.
                if (physicalRow <= 14) {
                    for (int z = 1; z <= 14; z++) {
                        remember(world, physical, physicalRow, 99, z, Material.MAGMA_BLOCK);
                        remember(world, physical, physicalRow, 101, z, Material.AIR);
                    }
                    physicalRow++; schedule(this::setup); return;
                }
                if (storage == null) {
                    storage = BukkitWorldManager.instance().getWorld(world.getUID()).storageWorld();
                    for (String name : IDLE) {
                        Class<?> type = Class.forName(PREFIX + "block.entity." + CLASS_NAMES.get(name));
                        types.put(name, type);
                        Method method = type.getDeclaredMethod("tick"); method.setAccessible(true);
                        methods.put(name, MethodHandles.lookup().unreflect(method).asType(MethodType.methodType(void.class, Object.class)));
                    }
                }
                int until = Math.min(ticks.length, prepared + BATCH);
                for (; prepared < until; prepared++) {
                    boolean active = prepared >= IDLE_COUNT;
                    String name = active ? ACTIVE[(prepared - IDLE_COUNT) % ACTIVE.length] : IDLE[prepared % IDLE.length];
                    int x = 1 + prepared % 14, z = 1 + (prepared / 14) % 14;
                    ImmutableBlockState state = CraftEngineBlocks.byId(Key.of("kaleidoscopecookery:" + name)).defaultState();
                    if (active && name.equals("stockpot")) state = flag(state, "has_lid", true);
                    if (active && name.equals("shawarma_spit")) state = flag(state, "powered", true);
                    BlockEntity entity = new BlockEntity(new BlockPos(x, 100, z), state); entity.setWorld(storage);
                    BlockEntityController controller = entity.controller.getAt(types.get(name).asSubclass(BlockEntityController.class), 0);
                    if (controller == null) throw new AssertionError("Missing real " + name + " controller");
                    controllers.add(controller);
                    if (active) {
                        active(controller, name);
                        activeDevices.add(new ActiveDevice(name, controller, progress(controller, name)));
                    }
                    ticks[prepared] = new Tick(controller, methods.get(name));
                }
                if (prepared < ticks.length) { schedule(this::setup); return; }
                allocation = ManagementFactory.getPlatformMXBean(com.sun.management.ThreadMXBean.class);
                allocationAvailable = allocation != null && allocation.isThreadAllocatedMemorySupported();
                if (allocationAvailable && !allocation.isThreadAllocatedMemoryEnabled()) allocation.setThreadAllocatedMemoryEnabled(true);
                cpuBean = ManagementFactory.getThreadMXBean();
                cpuAvailable = cpuBean.isCurrentThreadCpuTimeSupported();
                plugin.getLogger().info("LIVE_PERFORMANCE_WARMUP " + LIVE_WARMUP + " real game ticks");
                schedule(this::sample);
            } catch (Throwable error) { fail(error); }
        }

        void sample() {
            try {
                owner();
                if (!Bukkit.getOnlinePlayers().isEmpty()) throw new AssertionError("Players joined the isolated performance profile");
                long clock = world.getGameTime();
                if (clock == lastClock) { duplicateClockCallbacks++; schedule(this::sample); return; }
                lastClock = clock;
                if (warmup < LIVE_WARMUP) {
                    frame(ticks); warmup++;
                    if (warmup == LIVE_WARMUP) {
                        for (int i = 0; i < activeDevices.size(); i++) {
                            ActiveDevice device = activeDevices.get(i);
                            measuredBefore[i] = progress(device.controller(), device.type());
                            if (measuredBefore[i] <= device.initialProgress())
                                throw new AssertionError("Live warmup controller stalled: " + device.type() + " #" + i);
                        }
                        tasksBefore = pendingTasks(plugin); elapsedStart = System.nanoTime();
                    }
                    schedule(this::sample); return;
                }
                long thread = Thread.currentThread().threadId();
                long cpu = cpuAvailable ? cpuBean.getCurrentThreadCpuTime() : -1;
                long bytes = allocationAvailable ? allocation.getThreadAllocatedBytes(thread) : -1;
                long started = System.nanoTime();
                frame(ticks);
                durations[sampled] = System.nanoTime() - started;
                if (bytes >= 0) allocatedBytes += allocation.getThreadAllocatedBytes(thread) - bytes;
                if (cpu >= 0) cpuNanos += cpuBean.getCurrentThreadCpuTime() - cpu;
                clocks[sampled] = clock;
                if (++sampled < LIVE_SAMPLES) { schedule(this::sample); return; }
                completedReport = report();
                finishing = true; restoration.addAll(physical.entrySet()); schedule(this::cleanup);
            } catch (Throwable error) { fail(error); }
        }

        Map<String, Object> report() throws Exception {
            Map<String, Integer> minimum = new LinkedHashMap<>(), maximum = new LinkedHashMap<>();
            for (int i = 0; i < activeDevices.size(); i++) {
                ActiveDevice device = activeDevices.get(i);
                int delta = progress(device.controller(), device.type()) - measuredBefore[i];
                if (delta <= 0) throw new AssertionError("Live measured controller stalled: " + device.type() + " #" + i);
                minimum.merge(device.type(), delta, Math::min); maximum.merge(device.type(), delta, Math::max);
            }
            long[] sorted = durations.clone(); Arrays.sort(sorted);
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("measurement", "owner-thread controller CPU/allocation sampled once per real world game tick; not live TPS");
            report.put("idle_controllers", IDLE_COUNT); report.put("active_controllers", ACTIVE_COUNT);
            report.put("idle_types", List.of(IDLE)); report.put("active_types", List.of(ACTIVE));
            report.put("active_per_type", 50); report.put("active_progress_validated", activeDevices.size());
            report.put("measured_progress_min_by_type", minimum); report.put("measured_progress_max_by_type", maximum);
            report.put("warmup_frames", LIVE_WARMUP); report.put("sample_frames", LIVE_SAMPLES);
            report.put("world_game_time_samples", Arrays.stream(clocks).boxed().toList());
            report.put("duplicate_game_time_callbacks_skipped", duplicateClockCallbacks);
            report.put("wall_nanos", Arrays.stream(durations).sum());
            report.put("elapsed_wall_nanos", System.nanoTime() - elapsedStart);
            report.put("thread_cpu_nanos", cpuAvailable ? cpuNanos : null);
            report.put("mean_frame_nanos", Arrays.stream(durations).average().orElseThrow());
            report.put("median_frame_nanos", percentile(sorted, 0.5)); report.put("p95_frame_nanos", percentile(sorted, 0.95));
            report.put("p99_frame_nanos", percentile(sorted, 0.99)); report.put("nano_samples", Arrays.stream(durations).boxed().toList());
            report.put("thread_allocated_bytes", allocationAvailable ? allocatedBytes : null);
            report.put("allocated_bytes_per_frame", allocationAvailable ? allocatedBytes / (double) LIVE_SAMPLES : null);
            report.put("pending_bukkit_tasks_before_by_owner", tasksBefore);
            report.put("pending_bukkit_tasks_after_by_owner", pendingTasks(plugin));
            report.put("task_measurement_scope", "Bukkit pending tasks for CE/Cookery/probe owners; no internal CE ticker entries or Folia scheduler counters");
            report.put("packet_count", null);
            report.put("java", System.getProperty("java.version")); report.put("minecraft", Bukkit.getMinecraftVersion());
            report.put("cookery_version", Bukkit.getPluginManager().getPlugin("KaleidoscopeCookeryPlugin").getPluginMeta().getVersion());
            report.put("craftengine_version", Bukkit.getPluginManager().getPlugin("CraftEngine").getPluginMeta().getVersion());
            report.put("limitations", List.of("only controller invocation CPU and allocation are measured; scheduled wait/setup/cleanup are excluded",
                    "no online clients, no packet/off-thread allocation instrumentation, no animal or cross-region movement workload",
                    "old tea processing advances in batches of 23 ticks; progress totals are reported without assuming equal per-frame increments",
                    "construction, warmup and probe bookkeeping allocations excluded; host scheduling and GC may affect tails"));
            checks.add("performance: live-clock workload validates every one of 200 active controllers over 100 measured frames");
            plugin.getLogger().info("LIVE_PERFORMANCE_RESULT median=" + report.get("median_frame_nanos")
                    + "ns p95=" + report.get("p95_frame_nanos") + "ns allocated/frame=" + report.get("allocated_bytes_per_frame"));
            return report;
        }

        void fail(Throwable error) {
            if (failure == null) failure = error; else failure.addSuppressed(error);
            if (!finishing) {
                finishing = true; restoration.addAll(physical.entrySet()); schedule(this::cleanup);
            }
        }

        void cleanup() {
            try {
                owner();
                int until = Math.min(controllers.size(), cleanupController + BATCH);
                for (; cleanupController < until; cleanupController++) {
                    try { controllers.get(cleanupController).gatherElements(element -> element.deactivate()); }
                    catch (Throwable error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
                }
                if (cleanupController < controllers.size()) { schedule(this::cleanup); return; }
                until = Math.min(restoration.size(), cleanupBlock + BATCH);
                for (; cleanupBlock < until; cleanupBlock++) {
                    var entry = restoration.get(cleanupBlock); BlockPos position = entry.getKey();
                    try { world.getBlockAt(position.x(), position.y(), position.z()).setBlockData(entry.getValue(), false); }
                    catch (Throwable error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
                }
                if (cleanupBlock < restoration.size()) { schedule(this::cleanup); return; }
                if (failure == null) result.complete(completedReport); else result.completeExceptionally(failure);
            } catch (Throwable error) {
                if (failure == null) failure = error; else failure.addSuppressed(error);
                // A wrong-thread callback must not perform cleanup writes; retry on the declared world owner.
                schedule(this::cleanup);
            }
        }
    }

    private static void active(BlockEntityController controller, String kind) throws Exception {
        Item input = InventoryUtils.createOrEmpty(INPUT).copyWithCount(1);
        switch (kind) {
            case "stockpot" -> {
                ((List<Item>) get(controller, "ingredients")).add(input);
                Class<?> stage = Class.forName(PREFIX + "block.entity.StockpotStage");
                set(controller, "stage", Enum.valueOf((Class) stage, "COOKING"));
                set(controller, "currentTick", WORK);
                optionalPlan(controller, "cookingPlan", "planFlex", "STOCKPOT");
            }
            case "steamer" -> {
                ((Item[]) get(controller, "items"))[0] = input;
                set(controller, "itemCount", 1); set(controller, "hasLid", true);
                ((int[]) get(controller, "cookingTime"))[0] = WORK;
                optionalSlotPlan(controller, "STEAMER", false);
            }
            case "teapot" -> {
                set(controller, "input", input); set(controller, "fluid", Key.of("minecraft:water"));
                set(controller, "status", 1); set(controller, "currentTick", WORK);
                optionalPlan(controller, "cookingPlan", "planTeapot", "TEAPOT");
            }
            case "shawarma_spit" -> {
                ((Item[][]) get(controller, "items"))[0][0] = input;
                ((int[][]) get(controller, "cookingTime"))[0][0] = WORK;
                optionalSlotPlan(controller, "SHAWARMA", true);
            }
            default -> throw new AssertionError(kind);
        }
    }

    private static void optionalPlan(Object controller, String field, String lookup, String type) throws Exception {
        Field destination;
        try { destination = controller.getClass().getDeclaredField(field); }
        catch (NoSuchFieldException baseline) { return; }
        destination.setAccessible(true);
        Object registry = Class.forName(PREFIX + "recipe.FoodRecipeRegistry").getMethod("instance").invoke(null);
        Object plan;
        if (lookup.equals("planTeapot")) {
            plan = registry.getClass().getMethod(lookup, Key.class, Key.class).invoke(registry, Key.of("minecraft:water"), INPUT);
        } else {
            Class<?> appliance = Class.forName(PREFIX + "recipe.ApplianceType");
            Object enumeration = Enum.valueOf((Class) appliance, type);
            plan = registry.getClass().getMethod(lookup, appliance, List.class, Key.class, int.class)
                    .invoke(registry, enumeration, List.of(INPUT), Key.of("minecraft:water_bucket"), WORK);
        }
        plan = plan.getClass().getMethod("withWorkRequired", int.class).invoke(plan, WORK);
        destination.set(controller, plan);
    }

    private static int progress(Object controller, String type) throws Exception {
        return switch (type) {
            case "stockpot", "teapot" -> -((Integer) get(controller, "currentTick"));
            case "steamer" -> ((int[]) get(controller, "cookingProgress"))[0];
            case "shawarma_spit" -> ((int[][]) get(controller, "cookingProgress"))[0][0];
            default -> throw new AssertionError("No progress counter for " + type);
        };
    }

    private static Map<String, Integer> pendingTasks(JavaPlugin plugin) {
        try {
            Map<String, Integer> counts = new LinkedHashMap<>();
            counts.put("CraftEngine", 0);
            counts.put("KaleidoscopeCookeryPlugin", 0);
            counts.put(plugin.getName(), 0);
            for (org.bukkit.scheduler.BukkitTask task : Bukkit.getScheduler().getPendingTasks()) {
                String owner = task.getOwner().getName();
                if (counts.containsKey(owner)) counts.merge(owner, 1, Integer::sum);
            }
            return Map.copyOf(counts);
        } catch (RuntimeException unsupportedScheduler) {
            return null;
        }
    }

    private static void optionalSlotPlan(Object controller, String type, boolean layered) throws Exception {
        Object slots;
        try { slots = get(controller, "cookingPlans"); }
        catch (NoSuchFieldException baseline) { return; }
        Object registry = Class.forName(PREFIX + "recipe.FoodRecipeRegistry").getMethod("instance").invoke(null);
        Class<?> appliance = Class.forName(PREFIX + "recipe.ApplianceType");
        Object enumeration = Enum.valueOf((Class) appliance, type);
        Object plan = registry.getClass().getMethod("planAccurate", appliance, Key.class, int.class).invoke(registry, enumeration, INPUT, WORK);
        plan = plan.getClass().getMethod("withWorkRequired", int.class).invoke(plan, WORK);
        Array.set(layered ? Array.get(slots, 0) : slots, 0, plan);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ImmutableBlockState flag(ImmutableBlockState state, String property, boolean value) {
        return BlockStates.with(state, state.getProperty(property), value);
    }
    private static Object get(Object value, String name) throws Exception {
        Field field = value.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(value);
    }
    private static void set(Object value, String name, Object fieldValue) throws Exception {
        Field field = value.getClass().getDeclaredField(name); field.setAccessible(true); field.set(value, fieldValue);
    }
    private static void remember(World world, Map<BlockPos, BlockData> values, int x, int y, int z, Material material) {
        BlockPos position = new BlockPos(x, y, z);
        values.put(position, world.getBlockAt(x, y, z).getBlockData().clone());
        world.getBlockAt(x, y, z).setType(material, false);
    }
    private static void frame(Tick[] ticks) throws Exception {
        try { for (Tick tick : ticks) tick.run(); }
        catch (Throwable failure) {
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            throw new AssertionError(failure);
        }
    }
    private static long percentile(long[] sorted, double quantile) {
        return sorted[Math.max(0, Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * quantile) - 1))];
    }
}
