package net.kaleidoscope.cookery.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Fired when a player carves a portion off a shawarma spit.
 * Rewrite {@code product} to hand out something else, or cancel to leave the
 * spit untouched and give the player nothing.
 */
public class ShawarmaExtractEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Location location;
    private ItemStack product;
    private boolean cancelled;

    /**
     * @param who the carving player
     * @param location the spit furniture
     * @param product the portion about to be handed out
     */
    public ShawarmaExtractEvent(Player who, Location location, ItemStack product) {
        super(who);
        this.location = location;
        this.product = product;
    }

    /**
     * @return the spit furniture
     */
    public Location location() {
        return this.location;
    }

    /**
     * @return the portion about to be handed out
     */
    public ItemStack product() {
        return this.product;
    }

    /**
     * @param product the portion to hand out instead
     */
    public void setProduct(ItemStack product) {
        this.product = product;
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
