package net.kaleidoscope.cookery.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Fired when a player breaks a steamer that still holds finished food.
 * Cancelling skips only the special product drop; the steamer block itself is
 * still broken and drops as usual.
 */
public class SteamerBreakFullEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Location location;
    private final List<ItemStack> products;
    private boolean cancelled;

    /**
     * @param who the breaking player
     * @param location the steamer block
     * @param products the finished food about to drop
     */
    public SteamerBreakFullEvent(Player who, Location location, List<ItemStack> products) {
        super(who);
        this.location = location;
        this.products = products;
    }

    /**
     * @return the steamer block
     */
    public Location location() {
        return this.location;
    }

    /**
     * @return the finished food about to drop, mutable
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
