package net.kaleidoscope.cookery.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;

// 石磨磨完一批产出成品时触发；推磨者可能是玩家也可能是生物（player 为空表示生物拉磨）
public class MillstoneGrindCompleteEvent extends Event implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Player player;
    private final Location location;
    private final List<org.bukkit.inventory.ItemStack> products;
    private boolean cancelled;

    public MillstoneGrindCompleteEvent(Player player, Location location, List<org.bukkit.inventory.ItemStack> products) {
        this.player = player;
        this.location = location;
        this.products = products;
    }

    // 生物拉磨时为 null
    public Player player() {
        return this.player;
    }

    public Location location() {
        return this.location;
    }

    public List<org.bukkit.inventory.ItemStack> products() {
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
