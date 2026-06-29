package net.kaleidoscope.cookery.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.List;

// 石磨磨完一批产出成品时触发 推磨者可能是玩家也可能是生物
public class MillstoneGrindCompleteEvent extends Event implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Player player;
    private final Location location;
    private final List<ItemStack> products;
    private boolean cancelled;

    public MillstoneGrindCompleteEvent(Player player, Location location, List<ItemStack> products) {
        this.player = player;
        this.location = location;
        this.products = products;
    }

    /** @return 拉磨的玩家 生物拉磨则返回 null */
    public Player player() {
        return this.player;
    }

    public Location location() {
        return this.location;
    }

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
