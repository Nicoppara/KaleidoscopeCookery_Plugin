import net.kaleidoscope.cookery.block.behavior.SteamerBehavior;
import net.kaleidoscope.cookery.recipe.AccurateFoodRecipe;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.CookingPlan;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.edit.AccurateRecipeDraft;
import net.kaleidoscope.cookery.recipe.edit.RecipeEditService;
import net.kaleidoscope.cookery.recipe.edit.RecipeSourceIndex;
import net.kaleidoscope.cookery.util.FoliaUtil;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.scheduler.SchedulerTask;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Only the dedicated processing_probe.yml is edited. No automatic shutdown or production-pack writes. */
public final class ProcessingEditProbe extends JavaPlugin {
    private static final Key FIRST = key("processing_first");
    private static final Key MIDDLE = key("processing_middle");
    private static final Key LAST = key("processing_last");
    private static final Key TEMPLATE = key("processing_template");
    private static final List<Key> FACTORY_RECIPES = List.of(FIRST, MIDDLE, LAST);
    private static final List<String> FACTORY_ORDER = List.of("first", "middle", "last");
    private final List<String> checks = new ArrayList<>();
    private final List<String> saveCompletionThreads = Collections.synchronizedList(new ArrayList<>());
    private final List<Boolean> saveCompletionsOffOwner = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Object> recipeOrderDiagnostics = new LinkedHashMap<>();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final java.util.Set<CompletableFuture<?>> pendingWork = ConcurrentHashMap.newKeySet();
    private Location ownerLocation;
    private Path fixtureFile;
    private Path sourceAlias;
    private volatile Path physicalFixture;
    private volatile String originalSource;
    private FixtureSnapshot original;
    private CookingPlan frozen;
    private int frozenWork;
    private int ownerCallbacks;
    private int asynchronousReads;
    private boolean reloadSuccess;
    private String reloadCompletionThread;
    private SchedulerTask timeout;

    @Override public void onEnable() {
        String worldName = System.getProperty("cookery.processing.world", "processing-verification");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            finish(new IllegalStateException("Acceptance world is unavailable: " + worldName));
            return;
        }
        ownerLocation = new Location(world, 8, 100, 8);
        FoliaUtil.runLater(() -> guarded(this::start), 300L, ownerLocation);
    }

    private void start() throws Exception {
        check(Boolean.getBoolean("cookery.processing.acceptance"), "dedicated acceptance profile flag");
        check(ownerThread(), "edit probe starts on its owner thread");
        check(!ownerLocation.getWorld().getName().equals("world") && Bukkit.getOnlinePlayers().isEmpty(),
                "isolated world and no online players");
        String expectedCe = System.getProperty("cookery.processing.craftengine");
        check(expectedCe != null && expectedCe.equals(ceVersion()), "exact CraftEngine version");
        String file = System.getProperty("cookery.processing.fixture");
        check(file != null && !file.isBlank(), "explicit fixture file supplied");
        fixtureFile = Path.of(file).toAbsolutePath().normalize();
        check(fixtureFile.getFileName().toString().equals("processing_probe.yml"), "only dedicated fixture is writable");
        AccurateFoodRecipe middle = recipe(MIDDLE);
        sourceAlias = RecipeSourceIndex.instance().get(middle);
        check(sourceAlias != null && sourceAlias.getFileName().toString().equals("processing_probe.yml"),
                "middle recipe belongs to dedicated fixture");
        sourceAlias = sourceAlias.toAbsolutePath().normalize();
        for (Key id : List.of(FIRST, MIDDLE, LAST, TEMPLATE)) verifySource(recipe(id));
        recipeOrderDiagnostics.put("expected_recipe_ids", FACTORY_RECIPES.stream().map(Key::asString).toList());
        captureRecipeOrder("before_edits");
        check(middle.cookingTime() == 0 && recipe(TEMPLATE).cookingTime() == 31,
                "factory inherits appliance time and template initially inherits thirty-one ticks");
        var definition = CraftEngineBlocks.byId(key("steamer"));
        SteamerBehavior behavior = definition.defaultState().behavior().getFirst(SteamerBehavior.class);
        frozenWork = Math.max(1, behavior.cookingTime);
        frozen = FoodRecipeRegistry.instance().planAccurate(ApplianceType.STEAMER, middle.input(), frozenWork);
        check(frozen.valid() && frozen.matched() && frozen.recipeId().equals(MIDDLE)
                && frozen.workRequired() == frozenWork, "batch plan freezes original appliance duration before saving");
        timeout = FoliaUtil.runLater(() -> finish(new TimeoutException("Processing edit acceptance timed out")),
                1800L, ownerLocation);
        readFixture(snapshot -> {
            original = snapshot;
            recipeOrderDiagnostics.put("original_source_emission_ids", snapshot.order());
            check(snapshot.order().equals(FACTORY_ORDER) && snapshot.items().size() == 3,
                    "original factory emits three ordered recipes and three independent items");
            saveTime(MIDDLE, 47, this::afterMiddle);
        }, true);
    }

    private void afterMiddle() throws Exception {
        check(recipe(MIDDLE).cookingTime() == 47, "middle time hot-updates after asynchronous file save");
        for (Key id : FACTORY_RECIPES) verifySource(recipe(id));
        readFixture(snapshot -> {
            verifyFactory(snapshot, "middle save");
            check(snapshot.factoryCount() == 3, "middle edit splits factory into prefix, edited instance and suffix");
            check(snapshot.times().equals(Map.of("first", 0, "middle", 47, "last", 0)),
                    "only middle generated recipe changes its cooking time");
            saveTime(FIRST, 13, () -> {
                check(recipe(FIRST).cookingTime() == 13, "prefix source remains editable after middle factory split");
                verifySource(recipe(LAST));
                saveTime(LAST, 19, () -> {
                    check(recipe(LAST).cookingTime() == 19, "suffix source remains editable after multiple factory splits");
                    saveTime(TEMPLATE, 57, () -> {
                        check(recipe(TEMPLATE).cookingTime() == 57, "template recipe uses the explicit time override");
                        saveTime(TEMPLATE, 0, this::afterInheritance);
                    });
                });
            });
        }, false);
    }

    private void afterInheritance() throws Exception {
        check(recipe(TEMPLATE).cookingTime() == 31, "clearing template override restores inherited time in runtime metadata");
        for (Key id : List.of(FIRST, MIDDLE, LAST, TEMPLATE)) verifySource(recipe(id));
        readFixture(snapshot -> {
            verifyFactory(snapshot, "all saves");
            check(snapshot.times().equals(Map.of("first", 13, "middle", 47, "last", 19)),
                    "factory sibling time edits retain their original output order");
            check(snapshot.templatePresent() && !snapshot.templateHasTimeOverride() && snapshot.templateDefault() == 31,
                    "template reference and base time survive while the explicit override is removed from YAML");
            reload();
        }, false);
    }

    private void reload() {
        captureRecipeOrder("before_reload");
        CraftEngine ce = CraftEngine.instance();
        CompletableFuture<CraftEngine.ReloadResult> future = ce.reloadPlugin(ce.scheduler().async(),
                task -> ce.scheduler().platform().run(task), true, true);
        pendingWork.add(future);
        future.whenComplete((result, error) -> {
            pendingWork.remove(future);
            reloadCompletionThread = Thread.currentThread().getName();
            owner(() -> {
                if (error != null) throw new IllegalStateException("Actual CraftEngine reload failed", error);
                reloadSuccess = result != null && result.success();
                check(reloadSuccess, "actual CraftEngine reload future reports success");
                check(!ce.isReloading(), "actual reload completes before post-reload assertions");
                Map<String, Boolean> applianceDefinitions = new LinkedHashMap<>();
                for (String id : List.of("steamer", "shawarma_spit", "stockpot", "teapot", "pot", "chopping_board", "stove"))
                    applianceDefinitions.put(id, CraftEngineBlocks.byId(key(id)) != null);
                var millstone = CraftEngineFurniture.byId(key("new_millstone"));
                applianceDefinitions.put("new_millstone", millstone != null);
                applianceDefinitions.put("new_millstone_ground", millstone != null && millstone.getVariant("ground") != null);
                recipeOrderDiagnostics.put("post_reload_appliance_definitions", applianceDefinitions);
                for (var appliance : applianceDefinitions.entrySet())
                    check(appliance.getValue(), "actual reload retains real pack appliance definition " + appliance.getKey());
                check(recipe(FIRST).cookingTime() == 13 && recipe(MIDDLE).cookingTime() == 47
                        && recipe(LAST).cookingTime() == 19 && recipe(TEMPLATE).cookingTime() == 31,
                        "actual reload reads all saved recipe times and inherited template default");
                List<Key> loadedOrder = captureRecipeOrder("after_reload");
                check(loadedOrder.equals(FACTORY_RECIPES), "actual CE registration retains generated recipe order"
                        + "; expected=" + FACTORY_RECIPES + "; actual=" + loadedOrder);
                for (String id : FACTORY_ORDER) {
                    check(BukkitItemManager.instance().loadedItems().containsKey(key("processing_display_" + id)),
                            "actual reload retains unrelated generated display item " + id);
                }
                for (Key id : List.of(FIRST, MIDDLE, LAST, TEMPLATE)) verifySource(recipe(id));
                CookingPlan restored = CookingPlan.load(frozen.save());
                check(restored.valid() && restored.recipeId().equals(MIDDLE) && restored.workRequired() == frozenWork,
                        "saved running plan keeps its original duration across actual CE reload");
                check(restored.buildResult().isPresent(), "frozen batch result still builds after actual item registry reload");
                CookingPlan next = FoodRecipeRegistry.instance().planAccurate(ApplianceType.STEAMER,
                        recipe(MIDDLE).input(), frozenWork);
                check(next.valid() && next.matched() && next.recipeId().equals(MIDDLE) && next.workRequired() == 47,
                        "new batch after actual reload uses forty-seven ticks");
                check(saveCompletionsOffOwner.size() == 5 && saveCompletionsOffOwner.stream().allMatch(Boolean::booleanValue),
                        "all five changed file saves complete asynchronously away from the appliance owner");
                readFixture(snapshot -> {
                    verifyFactory(snapshot, "actual reload");
                    finish(null);
                }, false);
            });
        });
    }

    private void saveTime(Key id, int time, CheckedAction after) throws Exception {
        check(ownerThread(), "draft mutation starts on owner for " + id.value() + " time " + time);
        AccurateFoodRecipe current = recipe(id);
        verifySource(current);
        AccurateRecipeDraft draft = AccurateRecipeDraft.editing(current);
        draft.cookingTime(time);
        CompletableFuture<String> saved = RecipeEditService.saveAccurate(draft);
        pendingWork.add(saved);
        saved.whenComplete((errorMessage, error) -> {
            pendingWork.remove(saved);
            saveCompletionThreads.add(Thread.currentThread().getName());
            boolean offOwner;
            try { offOwner = !ownerThread(); }
            catch (Exception checkFailure) { offOwner = false; }
            saveCompletionsOffOwner.add(offOwner);
            owner(() -> {
                check(ownerThread(), "save callback returns to owner for " + id.value() + " time " + time);
                if (error != null) throw new IllegalStateException("Asynchronous save failed", error);
                check(errorMessage == null, "file save succeeds for " + id.value() + " time " + time
                        + (errorMessage == null ? "" : ": " + errorMessage));
                after.run();
            });
        });
    }

    private void readFixture(CheckedConsumer<FixtureSnapshot> after, boolean backup) {
        CompletableFuture<Void> inspected = new CompletableFuture<>();
        pendingWork.add(inspected);
        CraftEngine.instance().scheduler().async().execute(() -> {
            try {
                if (ownerThread()) throw new AssertionError("Fixture file inspection must run off owner thread");
                if (!Files.isSameFile(fixtureFile, sourceAlias)) throw new AssertionError("Recipe source escaped dedicated fixture file");
                String source = Files.readString(fixtureFile, StandardCharsets.UTF_8);
                if (backup) {
                    physicalFixture = fixtureFile.toRealPath();
                    originalSource = source;
                    Files.createDirectories(getDataFolder().toPath());
                    Path saved = getDataFolder().toPath().resolve("fixture-original-" + System.currentTimeMillis() + ".yml");
                    Files.writeString(saved, source, StandardCharsets.UTF_8);
                }
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.loadFromString(source);
                FixtureSnapshot snapshot = inspect(yaml);
                asynchronousReads++;
                inspected.complete(null);
                pendingWork.remove(inspected);
                owner(() -> after.accept(snapshot));
            } catch (Throwable error) {
                inspected.completeExceptionally(error);
                pendingWork.remove(inspected);
                owner(() -> { throw new IllegalStateException("Asynchronous fixture inspection failed", error); });
            }
        });
    }

    private FixtureSnapshot inspect(YamlConfiguration yaml) {
        List<String> order = new ArrayList<>();
        Map<String, Integer> times = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        int factoryCount = 0;
        for (String name : yaml.getKeys(false)) {
            if (!name.startsWith("config_factory#processing_fixture")) continue;
            ConfigurationSection factory = yaml.getConfigurationSection(name);
            if (factory == null) throw new AssertionError("Invalid fixture factory " + name);
            factoryCount++;
            ConfigurationSection blueprint = factory.getConfigurationSection("blueprint");
            if (blueprint == null) throw new AssertionError("Missing fixture blueprint");
            for (Map<?, ?> instance : factory.getMapList("instances")) {
                String id = String.valueOf(instance.get("id"));
                order.add(id);
                Map<String, String> arguments = new LinkedHashMap<>();
                instance.forEach((key, value) -> arguments.put(key.toString(), String.valueOf(value)));
                ConfigurationSection recipes = blueprint.getConfigurationSection("accurate_foods");
                ConfigurationSection generatedItems = blueprint.getConfigurationSection("items");
                if (recipes == null || generatedItems == null) throw new AssertionError("Factory lost a recipe or item output");
                for (String recipe : recipes.getKeys(false)) {
                    Map<?, ?> body = (Map<?, ?>) expand(recipes.get(recipe), arguments);
                    times.put(id, body.containsKey("cooking_time") ? ((Number) body.get("cooking_time")).intValue() : 0);
                    if (!expandString(recipe, arguments).equals("kaleidoscopecookery:processing_" + id))
                        throw new AssertionError("Generated recipe id changed");
                }
                for (String item : generatedItems.getKeys(false)) {
                    String generated = expandString(item, arguments);
                    if (items.put(generated, expand(generatedItems.get(item), arguments)) != null)
                        throw new AssertionError("Duplicate generated fixture item " + generated);
                }
            }
        }
        ConfigurationSection templateRecipe = yaml.getConfigurationSection("accurate_foods." + TEMPLATE.asString());
        ConfigurationSection templateDefinition = yaml.getConfigurationSection("templates.kaleidoscopecookery:processing_recipe_template");
        boolean hasOverride = false;
        if (templateRecipe != null) {
            for (String alias : List.of("cooking_time", "cooking-time", "time")) {
                hasOverride |= templateRecipe.contains(alias) || templateRecipe.contains("overrides." + alias)
                        || templateRecipe.contains("merges." + alias);
            }
        }
        return new FixtureSnapshot(List.copyOf(order), Map.copyOf(times), Map.copyOf(items), factoryCount,
                templateRecipe != null && templateRecipe.contains("template"), hasOverride,
                templateDefinition == null ? -1 : templateDefinition.getInt("cooking_time", -1));
    }

    private void verifyFactory(FixtureSnapshot snapshot, String phase) {
        recipeOrderDiagnostics.put("source_emission_" + phase, snapshot.order());
        check(snapshot.order().equals(original.order()), phase + " preserves factory emission order");
        check(snapshot.items().equals(original.items()), phase + " preserves all unrelated generated item definitions");
    }

    private List<Key> captureRecipeOrder(String phase) {
        FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
        return registry.readSnapshot(() -> {
            List<Key> accurateOrder = registry.accurateRecipes(ApplianceType.STEAMER).stream()
                    .map(AccurateFoodRecipe::id).filter(FACTORY_RECIPES::contains).toList();
            List<Key> menuOrder = registry.menuAccurateRecipes(ApplianceType.STEAMER).stream()
                    .map(AccurateFoodRecipe::id).filter(FACTORY_RECIPES::contains).toList();
            Map<String, Object> observed = new LinkedHashMap<>();
            observed.put("accurate_recipe_ids", accurateOrder.stream().map(Key::asString).toList());
            observed.put("menu_recipe_ids", menuOrder.stream().map(Key::asString).toList());
            Map<String, Object> sources = new LinkedHashMap<>();
            for (Key id : FACTORY_RECIPES) {
                AccurateFoodRecipe recipe = registry.findAccurateById(id);
                Map<String, Object> source = new LinkedHashMap<>();
                Path file = RecipeSourceIndex.instance().get(recipe);
                var target = RecipeSourceIndex.instance().target(recipe);
                source.put("file", file == null ? null : file.toString());
                source.put("generated_node", target == null ? null : target.generatedNode());
                source.put("factory_key", target == null ? null : target.factoryKey());
                source.put("instances_key", target == null ? null : target.instancesKey());
                source.put("instance_index", target == null ? null : target.instanceIndex());
                sources.put(id.asString(), source);
            }
            observed.put("sources", sources);
            recipeOrderDiagnostics.put(phase, observed);
            return accurateOrder;
        });
    }

    private static Object expand(Object value, Map<String, String> arguments) {
        if (value instanceof ConfigurationSection section) value = section.getValues(false);
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, item) -> result.put(expandString(key.toString(), arguments), expand(item, arguments)));
            return result;
        }
        if (value instanceof List<?> source) return source.stream().map(item -> expand(item, arguments)).toList();
        return value instanceof String text ? expandString(text, arguments) : value;
    }
    private static String expandString(String text, Map<String, String> arguments) {
        for (var argument : arguments.entrySet()) text = text.replace("${" + argument.getKey() + "}", argument.getValue());
        return text;
    }
    private record FixtureSnapshot(List<String> order, Map<String, Integer> times, Map<String, Object> items,
                                   int factoryCount, boolean templatePresent, boolean templateHasTimeOverride,
                                   int templateDefault) {}
    private AccurateFoodRecipe recipe(Key id) {
        AccurateFoodRecipe recipe = FoodRecipeRegistry.instance().findAccurateById(id);
        if (recipe == null) throw new AssertionError("Fixture recipe is missing: " + id);
        return recipe;
    }
    private void verifySource(AccurateFoodRecipe recipe) {
        Path source = RecipeSourceIndex.instance().get(recipe);
        check(source != null && source.toAbsolutePath().normalize().equals(sourceAlias)
                && RecipeSourceIndex.instance().target(recipe) != null,
                "source remains editable in fixture for " + recipe.id().value());
    }
    private void owner(CheckedAction action) {
        if (finished.get()) return;
        FoliaUtil.run(() -> guarded(() -> {
            check(ownerThread(), "asynchronous continuation resumes on owner");
            ownerCallbacks++;
            action.run();
        }), ownerLocation);
    }
    private void guarded(CheckedAction action) {
        if (finished.get()) return;
        try { action.run(); }
        catch (Throwable failure) { finish(failure); }
    }
    private boolean ownerThread() throws Exception {
        if (!FoliaUtil.isFolia()) return Bukkit.isPrimaryThread();
        if (ownerLocation == null) return false;
        Method owns = Bukkit.class.getMethod("isOwnedByCurrentRegion", Location.class);
        return (boolean) owns.invoke(null, ownerLocation);
    }
    private void check(boolean success, String description) {
        if (!success) throw new AssertionError(description);
        checks.add(description);
    }
    private String ceVersion() {
        var ce = Bukkit.getPluginManager().getPlugin("CraftEngine");
        return ce == null ? "missing" : ce.getDescription().getVersion();
    }
    private static Key key(String id) { return Key.of("kaleidoscopecookery:" + id); }
    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
    @FunctionalInterface private interface CheckedConsumer<T> { void accept(T value) throws Exception; }

    private void finish(Throwable failure) {
        if (!finished.compareAndSet(false, true)) return;
        if (timeout != null) timeout.cancel();
        CompletableFuture.allOf(pendingWork.toArray(CompletableFuture<?>[]::new)).whenCompleteAsync((ignored, pendingError) -> {
            Throwable finalFailure = failure == null ? pendingError : failure;
            boolean restored = false;
            try {
                if (originalSource != null && physicalFixture != null) {
                    restoreFixture();
                    restored = true;
                    checks.add("original fixture bytes restored after all save and reload futures complete");
                }
            } catch (Throwable restoreFailure) {
                if (finalFailure == null) finalFailure = restoreFailure;
                else finalFailure.addSuppressed(restoreFailure);
            }
            if (finalFailure == null) getLogger().info("PROCESSING_EDIT_PROBE_PASS " + checks.size() + " checks");
            else getLogger().log(java.util.logging.Level.SEVERE, "PROCESSING_EDIT_PROBE_FAIL", finalFailure);
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("passed", finalFailure == null);
            report.put("craftengine", ceVersion());
            report.put("server", Bukkit.getVersion());
            report.put("folia", FoliaUtil.isFolia());
            report.put("scope", "Dedicated fixture only; actual asynchronous saves and actual CraftEngine reload; report follows completed file restoration; no graphical client");
            report.put("fixture", fixtureFile == null ? null : fixtureFile.toString());
            report.put("source_alias", sourceAlias == null ? null : sourceAlias.toString());
            report.put("physical_fixture", physicalFixture == null ? null : physicalFixture.toString());
            report.put("restore_completed", restored);
            report.put("restore_skipped_before_backup", originalSource == null);
            report.put("check_count", checks.size());
            report.put("checks", List.copyOf(checks));
            report.put("save_completion_threads", List.copyOf(saveCompletionThreads));
            report.put("save_completions_off_owner", List.copyOf(saveCompletionsOffOwner));
            report.put("owner_callbacks", ownerCallbacks);
            report.put("asynchronous_fixture_reads", asynchronousReads);
            report.put("reload_success", reloadSuccess);
            report.put("reload_completion_thread", reloadCompletionThread);
            report.put("frozen_original_work", frozenWork);
            report.put("recipe_order_diagnostics", recipeOrderDiagnostics);
            report.put("error", finalFailure == null ? null : finalFailure.toString());
            try {
                Files.createDirectories(getDataFolder().toPath());
                Files.writeString(getDataFolder().toPath().resolve("processing-edit-result.json"), json(report), StandardCharsets.UTF_8);
            } catch (Exception error) {
                getLogger().log(java.util.logging.Level.SEVERE, "Cannot save edit acceptance report", error);
            }
        }, CraftEngine.instance().scheduler().async());
    }
    private void restoreFixture() throws Exception {
        if (!physicalFixture.getFileName().toString().equals("processing_probe.yml")
                || !Files.isSameFile(fixtureFile, sourceAlias) || !Files.isSameFile(fixtureFile, physicalFixture))
            throw new IllegalStateException("Fixture restore target changed or escaped its dedicated file");
        Path temporary = Files.createTempFile(physicalFixture.getParent(), ".processing-restore-", ".yml");
        try {
            Files.writeString(temporary, originalSource, StandardCharsets.UTF_8);
            try { Files.move(temporary, physicalFixture, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, physicalFixture, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!Files.readString(physicalFixture, StandardCharsets.UTF_8).equals(originalSource))
                throw new IllegalStateException("Restored fixture differs from its original bytes");
        } finally { Files.deleteIfExists(temporary); }
    }
    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().map(entry -> quote(entry.getKey().toString()) + ":" + json(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Iterable<?> entries) {
            List<String> items = new ArrayList<>();
            entries.forEach(item -> items.add(json(item)));
            return String.join(",", items).transform(text -> '[' + text + ']');
        }
        return quote(value.toString());
    }
    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + '"';
    }
}
