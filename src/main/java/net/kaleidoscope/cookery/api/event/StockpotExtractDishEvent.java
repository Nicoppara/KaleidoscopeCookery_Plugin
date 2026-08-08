package net.kaleidoscope.cookery.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Fired when a player serves a finished dish out of a stockpot.
 * Rewrite {@code dish} to hand out something else, or cancel to keep the dish
 * in the pot and give the player nothing.
 */
public class StockpotExtractDishEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Location location;
    private ItemStack dish;
    private boolean cancelled;

    /**
     * @param who the serving player
     * @param location the stockpot block
     * @param dish the dish about to be handed out
     */
    public StockpotExtractDishEvent(Player who, Location location, ItemStack dish) {
        super(who);
        this.location = location;
        this.dish = dish;
    }

    /**
     * @return the stockpot block
     */
    public Location location() {
        return this.location;
    }

    /**
     * @return the dish about to be handed out
     */
    public ItemStack dish() {
        return this.dish;
    }

    /**
     * @param dish the dish to hand out instead
     */
    public void setDish(ItemStack dish) {
        this.dish = dish;
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
