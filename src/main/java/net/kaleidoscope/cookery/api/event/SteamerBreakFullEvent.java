package net.kaleidoscope.cookery.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

// 玩家打破装满的蒸笼时触发 products 为即将掉落的成品 可取消特殊掉落
public class SteamerBreakFullEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Location location;
    private final List<ItemStack> products;
    private boolean cancelled;

    public SteamerBreakFullEvent(Player who, Location location, List<ItemStack> products) {
        super(who);
        this.location = location;
        this.products = products;
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
