package net.kaleidoscope.cookery.api.event;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired once per candidate block while a sickle sweeps an area.
 * Cancelling skips the built-in harvest for that block, which is how a plugin
 * takes over a custom crop: cancel, harvest it yourself, then call
 * {@link #setCostDurability(boolean)} so the swing still wears the sickle down.
 */
public class SickleHarvestEvent extends Event implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Player player;
    private final ItemStack sickle;
    private final Block block;
    private boolean costDurability;
    private boolean cancelled;

    /**
     * @param player the sweeping player
     * @param sickle the sickle in hand
     * @param block the block about to be harvested
     */
    public SickleHarvestEvent(Player player, ItemStack sickle, Block block) {
        this.player = player;
        this.sickle = sickle;
        this.block = block;
    }

    /**
     * @return the sweeping player
     */
    public Player player() {
        return this.player;
    }

    /**
     * @return the sickle in hand
     */
    public ItemStack sickle() {
        return this.sickle;
    }

    /**
     * @return the block about to be harvested
     */
    public Block block() {
        return this.block;
    }

    /**
     * @return whether a cancelled block still costs sickle durability, false by default
     */
    public boolean costDurability() {
        return this.costDurability;
    }

    /**
     * @param costDurability whether this cancelled block still wears the sickle
     */
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
