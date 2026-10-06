import net.kaleidoscope.cookery.util.FoliaUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Standalone entrypoint for baseline/candidate tick measurements, with no new recipe API linkage.
 * Package only this class and ProcessingPerformanceProbe*.class; plugin.yml main is this class.
 * The external registered-profile runner owns the server lifecycle; this plugin never stops it.
 */
public final class ProcessingPerformanceRunner extends JavaPlugin {
    private final List<String> checks = new ArrayList<>();
    private Map<String, Object> performance = Map.of();
    private boolean originalForceLoad;
    private boolean changedForceLoad;

    @Override public void onEnable() {
        String name = System.getProperty("cookery.processing.world", "processing-verification");
        if (!Boolean.getBoolean("cookery.processing.acceptance") || name.isBlank() || name.equals("world")) {
            writeReport(new IllegalStateException("Explicit acceptance flag and isolated world are required"));
            return;
        }
        World world = Bukkit.getWorld(name);
        if (world == null) {
            writeReport(new IllegalStateException("Acceptance world is unavailable: " + name));
            return;
        }
        Bukkit.getGlobalRegionScheduler().run(this, task -> prepareWorld(world));
    }

    private void prepareWorld(World world) {
        try {
            if (!Bukkit.getOnlinePlayers().isEmpty()) throw new AssertionError("Acceptance must have no players online");
            String exact = System.getProperty("cookery.processing.craftengine");
            String actual = Bukkit.getPluginManager().getPlugin("CraftEngine").getPluginMeta().getVersion();
            if (exact == null || !exact.equals(actual)) throw new AssertionError("Exact CraftEngine version differs: " + actual);
            if (!Bukkit.getPluginManager().isPluginEnabled("KaleidoscopeCookeryPlugin"))
                throw new AssertionError("Cookery plugin is not enabled");
            originalForceLoad = world.isChunkForceLoaded(0, 0);
            world.setChunkForceLoaded(0, 0, true);
            changedForceLoad = true;
            // Chunk tickets belong to Folia's global region; chunk access and controllers belong to their owner.
            FoliaUtil.runLater(() -> measure(world), 100L, new Location(world, 8, 100, 8));
        } catch (Throwable error) { finishGlobal(world, error); }
    }

    private void measure(World world) {
        try {
            if (!Bukkit.isOwnedByCurrentRegion(world, 0, 0) || !Bukkit.getOnlinePlayers().isEmpty())
                throw new AssertionError("Acceptance must run on its owner with no players online");
            world.getChunkAt(0, 0).load();
            checks.add("performance: independent runner does not load CookingPlan or functional probe classes");
            int repeats = Integer.parseInt(System.getProperty("cookery.processing.repeats", "5"));
            if (repeats < 1 || repeats > 10) throw new IllegalArgumentException("cookery.processing.repeats must be between 1 and 10");
            List<Map<String, Object>> runs = new ArrayList<>(repeats);
            Map<String, Object> repeated = new LinkedHashMap<>();
            repeated.put("repeat_count", repeats);
            repeated.put("runs", runs);
            performance = repeated;
            repeat(world, repeats, runs, repeated);
        } catch (Throwable error) {
            finish(world, error);
        }
    }

    private void repeat(World world, int repeats, List<Map<String, Object>> runs, Map<String, Object> repeated) {
        getLogger().info("PERFORMANCE_REPEAT " + (runs.size() + 1) + "/" + repeats);
        ProcessingPerformanceProbe.measureAsync(this, world, checks).whenComplete((run, error) -> {
            try {
                if (error != null) { finish(world, error); return; }
                run.put("repeat_index", runs.size() + 1);
                runs.add(run);
                if (runs.size() < repeats) { repeat(world, repeats, runs, repeated); return; }
                repeated.put("aggregate", aggregate(runs));
                finish(world, null);
            } catch (Throwable continuationFailure) { finish(world, continuationFailure); }
        });
    }

    private void finish(World world, Throwable failure) {
        // The sampling future has already restored physical blocks on the owner before this handoff.
        Bukkit.getGlobalRegionScheduler().run(this, task -> finishGlobal(world, failure));
    }

    private void finishGlobal(World world, Throwable failure) {
        Throwable reportFailure = failure;
        try { if (changedForceLoad) world.setChunkForceLoaded(0, 0, originalForceLoad); }
        catch (Throwable cleanup) { if (reportFailure == null) reportFailure = cleanup; else reportFailure.addSuppressed(cleanup); }
        if (reportFailure == null) getLogger().info("PROCESSING_PROBE_PASS " + checks.size() + " checks");
        else getLogger().log(java.util.logging.Level.SEVERE, "PROCESSING_PROBE_FAIL", reportFailure);
        writeReport(reportFailure);
    }

    private static Map<String, Object> aggregate(List<Map<String, Object>> runs) {
        Map<String, Object> aggregate = new LinkedHashMap<>();
        aggregate.put("aggregation", "median of per-run metrics; percentile metrics are not pooled frame percentiles");
        for (String metric : List.of("median_frame_nanos", "p95_frame_nanos", "p99_frame_nanos", "allocated_bytes_per_frame")) {
            double[] values = runs.stream().map(run -> run.get(metric)).filter(java.util.Objects::nonNull)
                    .mapToDouble(value -> ((Number) value).doubleValue()).sorted().toArray();
            if (values.length == 0) {
                aggregate.put("median_of_run_" + metric, null);
                continue;
            }
            int middle = values.length / 2;
            double median = values.length % 2 == 0 ? (values[middle - 1] + values[middle]) / 2 : values[middle];
            aggregate.put("median_of_run_" + metric, median);
            aggregate.put("min_run_" + metric, values[0]);
            aggregate.put("max_run_" + metric, values[values.length - 1]);
        }
        aggregate.put("all_active_progress_validated", runs.stream().allMatch(run -> ((Number) run.get("active_progress_validated")).intValue() == 200));
        return aggregate;
    }

    private void writeReport(Throwable failure) {
        try {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("success", failure == null);
            report.put("checks", checks);
            report.put("check_count", checks.size());
            report.put("performance", performance);
            report.put("minecraft", Bukkit.getMinecraftVersion());
            report.put("craftengine", Bukkit.getPluginManager().getPlugin("CraftEngine").getPluginMeta().getVersion());
            report.put("cookery", Bukkit.getPluginManager().getPlugin("KaleidoscopeCookeryPlugin").getPluginMeta().getVersion());
            report.put("mode", "standalone-performance-only");
            report.put("error", failure == null ? null : failure.toString());
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(getDataFolder().toPath().resolve("result.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
        } catch (Throwable reportFailure) {
            getLogger().log(java.util.logging.Level.SEVERE, "PERFORMANCE_REPORT_FAIL", reportFailure);
        }
    }
}
