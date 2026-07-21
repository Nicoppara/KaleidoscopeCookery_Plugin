package net.kaleidoscope.cookery.api.event;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

// 镰刀范围收割时对每个候选方块触发一次 取消则该方块不走默认收割
// 想接管自定义作物就取消本事件并自行处理 再按需 setCostDurability(true) 让这次仍算耐久
public class SickleHarvestEvent extends Event implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Player player;
    private final ItemStack sickle;
    private final Block block;
    private boolean costDurability;
    private boolean cancelled;

    public SickleHarvestEvent(Player player, ItemStack sickle, Block block) {
        this.player = player;
        this.sickle = sickle;
        this.block = block;
    }

    public Player player() {
        return this.player;
    }

    public ItemStack sickle() {
        return this.sickle;
    }

    public Block block() {
        return this.block;
    }

    /** @return 取消后这次是否仍计入镰刀耐久消耗 默认不计 */
    public boolean costDurability() {
        return this.costDurability;
    }

    public void setCostDurability(boolean costDurability) {
        this.costDurability = costDurability;
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
