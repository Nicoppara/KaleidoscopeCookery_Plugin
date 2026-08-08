package net.kaleidoscope.cookery.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Gate that decides whether stirring a pot counts as cooking progress.
 * The built-in rule requires the pot to sit on a lit heat source and to have
 * been oiled; registering a condition here replaces that rule, so a plugin can
 * drop the oil requirement, demand a different item, or restrict cooking to
 * certain players.
 * Conditions are queried in registration order — the first returning a non-null
 * {@link Verdict} decides, {@code null} defers to the next, and the built-in
 * rule applies when every condition defers.
 * A denied verdict still plays the stir animation, it only withholds progress.
 * The optional message goes to the action bar as plain text or a translation key.
 */
@SuppressWarnings("unused")
public final class PotCookConditions {

    /**
     * Snapshot of a pot at the moment a player stirs it.
     *
     * @param location pot block location
     * @param oiled whether oil has been poured into the pot
     * @param heated whether a lit heat source sits below the pot
     * @param ingredients current pot contents, in the order they were added
     * @param stirFryCount stirs already credited for the current dish
     */
    public record PotState(@NotNull Location location, boolean oiled, boolean heated,
                           @NotNull List<ItemStack> ingredients, int stirFryCount) {
    }

    /**
     * Outcome of a cook condition.
     *
     * @param allowed whether this stir counts as cooking progress
     * @param message action bar text or translation key shown when denied, may be {@code null}
     */
    public record Verdict(boolean allowed, @Nullable String message) {

        /** Allows cooking without showing anything. */
        public static final Verdict ALLOW = new Verdict(true, null);

        /**
         * Denies cooking and tells the player why.
         *
         * @param message action bar text or translation key
         * @return the denying verdict
         */
        public static Verdict deny(@Nullable String message) {
            return new Verdict(false, message);
        }
    }

    /**
     * Decides whether a pot may cook.
     */
    @FunctionalInterface
    public interface Condition {

        /**
         * Tests one stir.
         *
         * @param player the stirring player
         * @param pot the pot snapshot
         * @return the verdict, or {@code null} to defer to the next condition
         */
        @Nullable Verdict test(@NotNull Player player, @NotNull PotState pot);
    }

    private static final PotCookConditions INSTANCE = new PotCookConditions();

    private final List<Condition> conditions = new CopyOnWriteArrayList<>();

    private PotCookConditions() {
    }

    /**
     * Returns the shared condition registry.
     *
     * @return the singleton registry
     */
    public static PotCookConditions instance() {
        return INSTANCE;
    }

    /**
     * Adds a condition.
     *
     * @param condition condition queried before the built-in rule
     */
    public void addCondition(@NotNull Condition condition) {
        conditions.add(condition);
    }

    /**
     * Removes a previously added condition.
     *
     * @param condition the condition to remove
     * @return {@code true} if it was registered
     */
    public boolean removeCondition(@NotNull Condition condition) {
        return conditions.remove(condition);
    }

    /**
     * Drops every registered condition, restoring the built-in rule.
     */
    public void clear() {
        conditions.clear();
    }

    /**
     * Reports whether any condition is registered. The pot checks this before
     * building a {@link PotState}, so the built-in path costs nothing.
     *
     * @return {@code true} if at least one condition is registered
     */
    public boolean hasConditions() {
        return !conditions.isEmpty();
    }

    /**
     * Queries the registered conditions in order.
     *
     * @param player the stirring player
     * @param pot the pot snapshot
     * @return the first non-null verdict, or {@code null} if every condition deferred
     */
    public @Nullable Verdict evaluate(@NotNull Player player, @NotNull PotState pot) {
        for (Condition condition : conditions) {
            Verdict verdict = condition.test(player, pot);
            if (verdict != null) {
                return verdict;
            }
        }
        return null;
    }
}
