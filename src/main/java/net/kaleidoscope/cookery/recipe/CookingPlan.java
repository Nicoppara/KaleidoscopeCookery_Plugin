package net.kaleidoscope.cookery.recipe;

import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import net.momirealms.craftengine.libraries.nbt.StringTag;
import net.momirealms.craftengine.libraries.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Immutable, versioned batch data. It never retains a live Item, world or registry snapshot. */
public record CookingPlan(boolean valid, boolean matched, Key recipeId, int workRequired,
                          int ingredientCount, Key carrier, List<Output> outputs,
                          List<String> choppingValues) {
    private static final int FORMAT = 1;
    private static final int MAX_OUTPUTS = 256;

    public record Output(Key key, int count, List<String> lore, DishQuality quality) {
        public Output {
            if (key == null || count < 1) throw new IllegalArgumentException("Invalid planned output");
            lore = List.copyOf(lore);
        }
    }

    public CookingPlan {
        if (workRequired < 1 || ingredientCount < 1) throw new IllegalArgumentException("Invalid batch work");
        outputs = List.copyOf(outputs);
        choppingValues = List.copyOf(choppingValues);
        if (outputs.size() > MAX_OUTPUTS) throw new IllegalArgumentException("Too many planned outputs");
        if (matched && (recipeId == null || outputs.isEmpty())) throw new IllegalArgumentException("Missing matched output");
    }

    public static CookingPlan unmatched(int defaultWork) {
        return new CookingPlan(true, false, null, Math.max(1, defaultWork), 1, null, List.of(), List.of());
    }

    public static CookingPlan invalid() {
        return new CookingPlan(false, false, null, 1, 1, null, List.of(), List.of());
    }

    public CookingPlan withWorkRequired(int work) {
        return new CookingPlan(valid, matched, recipeId, Math.max(1, work), ingredientCount, carrier, outputs, choppingValues);
    }

    /** Call only on the appliance's owning thread, immediately before committing completion. */
    public Optional<List<Item>> buildOutputs() {
        if (!valid || !matched) return Optional.empty();
        try {
        List<Item> built = new ArrayList<>(outputs.size());
        for (Output output : outputs) {
            Item item = output.quality() == null ? InventoryUtils.createOrEmpty(output.key())
                    : FlexMatcher.buildDish(output.key(), output.quality());
            if (ItemUtils.isEmpty(item)) return Optional.empty();
            if (!output.lore().isEmpty()) {
                item.loreComponent(output.lore().stream()
                        .map(line -> AdventureHelper.miniMessage().deserialize("<!i>" + line)).toList());
            }
            built.add(item.copyWithCount(output.count()));
        }
        return Optional.of(List.copyOf(built));
        } catch (RuntimeException unavailableOutput) {
            return Optional.empty();
        }
    }

    public Optional<FoodRecipeResult> buildResult() {
        return buildOutputs().filter(items -> items.size() == 1)
                .map(items -> new FoodRecipeResult(items.getFirst(), outputs.getFirst().count(), carrier));
    }

    public CompoundTag save() {
        CompoundTag data = new CompoundTag();
        data.putInt("version", FORMAT);
        data.putBoolean("valid", valid);
        data.putBoolean("matched", matched);
        data.putInt("work", workRequired);
        data.putInt("ingredient_count", ingredientCount);
        if (recipeId != null) data.putString("recipe", recipeId.asString());
        if (carrier != null) data.putString("carrier", carrier.asString());
        ListTag entries = new ListTag();
        for (Output output : outputs) {
            CompoundTag entry = new CompoundTag();
            entry.putString("item", output.key().asString());
            entry.putInt("count", output.count());
            if (output.quality() != null) entry.putString("quality", output.quality().name());
            entry.put("lore", strings(output.lore()));
            entries.add(entry);
        }
        data.put("outputs", entries);
        data.put("models", strings(choppingValues));
        return data;
    }

    /** A malformed saved plan is blocked, never treated as a new batch or re-rolled. */
    public static CookingPlan load(CompoundTag data) {
        try {
            if (data == null || data.getInt("version", -1) != FORMAT) return invalid();
            ListTag entries = data.getList("outputs");
            if (entries == null || entries.size() > MAX_OUTPUTS) return invalid();
            List<Output> outputs = new ArrayList<>(entries.size());
            for (Tag tag : entries) {
                if (!(tag instanceof CompoundTag entry)) return invalid();
                String quality = entry.getString("quality", "");
                outputs.add(new Output(Key.of(entry.getString("item", "")), entry.getInt("count", 0),
                        readStrings(entry.getList("lore")), quality.isEmpty() ? null : DishQuality.valueOf(quality)));
            }
            String recipe = data.getString("recipe", "");
            String carrier = data.getString("carrier", "");
            return new CookingPlan(data.getBoolean("valid", false), data.getBoolean("matched", false),
                    recipe.isEmpty() ? null : Key.of(recipe), data.getInt("work", 0),
                    data.getInt("ingredient_count", 1), carrier.isEmpty() ? null : Key.of(carrier),
                    outputs, readStrings(data.getList("models")));
        } catch (RuntimeException malformed) {
            return invalid();
        }
    }

    private static ListTag strings(List<String> values) {
        ListTag list = new ListTag();
        for (String value : values) list.add(new StringTag(value));
        return list;
    }

    private static List<String> readStrings(ListTag list) {
        if (list == null) throw new IllegalArgumentException("Invalid string list");
        List<String> values = new ArrayList<>(list.size());
        for (Tag tag : list) {
            if (!(tag instanceof StringTag value)) throw new IllegalArgumentException("Invalid string entry");
            values.add(value.getAsString());
        }
        return List.copyOf(values);
    }
}
