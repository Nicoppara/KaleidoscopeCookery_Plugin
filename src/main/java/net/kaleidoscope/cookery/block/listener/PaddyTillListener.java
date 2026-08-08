package net.kaleidoscope.cookery.block.listener;

import net.kaleidoscope.cookery.api.BlockTags;
import net.kaleidoscope.cookery.util.EventUtils;
import net.kaleidoscope.cookery.util.Hands;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;


// 锄头右键水下的泥土开成耕地
public final class PaddyTillListener implements Listener {
    // 泡在水里的耕地湿度恒满 开出来就给到位 省得等第一次 randomTick
    private static final Farmland TILLED_FARMLAND = tilledFarmland();

    private static Farmland tilledFarmland() {
        Farmland farmland = (Farmland) Bukkit.createBlockData(Material.FARMLAND);
        farmland.setMoisture(farmland.getMaximumMoisture());
        return farmland;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onTill(PlayerInteractEvent event) {
        // 只认主手 主副手各一把锄头时副手那个包会再触发一次 开两次地扣两次耐久
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack held = event.getItem();
        if (held == null || !Tag.ITEMS_HOES.isTagged(held.getType())) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !isTillable(block)) {
            return;
        }
        if (block.getRelative(0, 1, 0).getType() != Material.WATER) {
            return;
        }
        Player player = event.getPlayer();
        if (!InteractGuard.canBreak(BukkitAdaptor.adapt(player),
                BukkitAdaptor.adapt(block.getWorld()), block.getX(), block.getY(), block.getZ())) {
            return;
        }
        // 只为让日志插件记下原方块 取消结果不理会 权限已经走过 InteractGuard
        EventUtils.logBlockBreak(block, player);
        block.setBlockData(TILLED_FARMLAND, true);
        event.setCancelled(true);
        block.getWorld().playSound(block.getLocation(), Sound.ITEM_HOE_TILL, SoundCategory.BLOCKS, 1.0f, 1.0f);
        Hands.swing(player, event.getHand());
        damageHoe(player, held);
    }

    // CE 自定义方块的 getType 返回的是视觉方块 拿它查材质会误判 必须先分流
    // 两边查的是同一个标签键 自定义方块写在 settings.tags 原版方块写在 block_tags 配置段
    private boolean isTillable(Block block) {
        ImmutableBlockState custom = CraftEngineBlocks.getCustomBlockState(block);
        if (custom != null && !custom.isEmpty()) {
            return custom.settings().tags().contains(BlockTags.TILLABLE);
        }
        return BlockTags.instance().matches(BlockTags.TILLABLE, block.getType());
    }

    // 走 ItemStack#damage 而不是手写 damage 值 它会吃耐久附魔 发 ItemDamage/ItemBreak 事件 断裂还有音效
    private void damageHoe(Player player, ItemStack hoe) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        hoe.damage(1, player);
    }
}
