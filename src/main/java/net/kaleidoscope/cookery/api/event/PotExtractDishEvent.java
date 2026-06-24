package net.kaleidoscope.cookery.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

// 玩家从炒锅盛出成品时触发，可改写 dish 或取消
public class PotExtractDishEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Location location;
    private ItemStack dish;
    private boolean cancelled;

    public PotExtractDishEvent(Player who, Location location, ItemStack dish) {
        super(who);
        this.location = location;
        this.dish = dish;
    }

    public Location location() {
        return this.location;
    }

    public ItemStack dish() {
        return this.dish;
    }

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
