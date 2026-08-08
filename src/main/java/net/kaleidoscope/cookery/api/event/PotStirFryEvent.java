package net.kaleidoscope.cookery.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired each time a player stirs a cooking pot.
 * Cancelling makes the stir play its animation without counting towards
 * progress, so the dish never finishes on that stir.
 */
public class PotStirFryEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Location location;
    private final int count;
    private boolean cancelled;

    /**
     * @param who the stirring player
     * @param location the pot block
     * @param count stirs accumulated after this one
     */
    public PotStirFryEvent(Player who, Location location, int count) {
        super(who);
        this.location = location;
        this.count = count;
    }

    /**
     * @return the pot block
     */
    public Location location() {
        return this.location;
    }

    /**
     * @return stirs accumulated after this one
     */
    public int count() {
        return this.count;
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
