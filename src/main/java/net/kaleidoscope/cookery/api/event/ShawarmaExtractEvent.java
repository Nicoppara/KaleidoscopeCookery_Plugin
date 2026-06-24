package net.kaleidoscope.cookery.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

// 玩家从沙威玛烤架取出成品时触发，可改写 product 或取消
public class ShawarmaExtractEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Location location;
    private ItemStack product;
    private boolean cancelled;

    public ShawarmaExtractEvent(Player who, Location location, ItemStack product) {
        super(who);
        this.location = location;
        this.product = product;
    }

    public Location location() {
        return this.location;
    }

    public ItemStack product() {
        return this.product;
    }

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
