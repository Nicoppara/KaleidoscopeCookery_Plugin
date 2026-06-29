package net.kaleidoscope.cookery.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

// 玩家翻炒炒锅一次时触发 count 为本次翻炒后的累计次数
public class PotStirFryEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Location location;
    private final int count;
    private boolean cancelled;

    public PotStirFryEvent(Player who, Location location, int count) {
        super(who);
        this.location = location;
        this.count = count;
    }

    public Location location() {
        return this.location;
    }

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
