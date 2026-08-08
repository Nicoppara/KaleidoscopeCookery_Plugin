package net.kaleidoscope.cookery.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Fired when a millstone finishes grinding a batch.
 * The millstone can be turned by a player pushing the bar or by a harnessed
 * animal, so {@link #player()} is null in the animal case.
 * Cancelling withholds the products; the batch is still consumed.
 */
public class MillstoneGrindCompleteEvent extends Event implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Player player;
    private final Location location;
    private final List<ItemStack> products;
    private boolean cancelled;

    /**
     * @param player the pushing player, or null when an animal pulls
     * @param location the millstone furniture
     * @param products the ground output about to be ejected
     */
    public MillstoneGrindCompleteEvent(Player player, Location location, List<ItemStack> products) {
        this.player = player;
        this.location = location;
        this.products = products;
    }

    /**
     * @return the pushing player, or null when an animal pulls
     */
    public Player player() {
        return this.player;
    }

    /**
     * @return the millstone furniture
     */
    public Location location() {
        return this.location;
    }

    /**
     * @return the ground output about to be ejected, mutable
     */
    public List<ItemStack> products() {
        return this.products;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
