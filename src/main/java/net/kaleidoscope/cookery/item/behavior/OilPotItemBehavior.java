package net.kaleidoscope.cookery.item.behavior;

import net.kaleidoscope.cookery.block.entity.OilPotController;
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.momirealms.craftengine.bukkit.block.BukkitBlockManager;
import net.momirealms.craftengine.bukkit.item.behavior.BlockItemBehavior;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.pack.PendingConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigConstants;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigValue;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;

import java.nio.file.Path;
import java.util.Map;

// 油壶物品 放下来时把耐久换算成方块里的油量
// 耐久条是倒过来的 damage 0 表示满壶 所以 油量 = max_oil - damage
public class OilPotItemBehavior extends BlockItemBehavior {
    public static final ItemBehaviorFactory<OilPotItemBehavior> FACTORY = new Factory();
    // 默认值取自模组 OilPotBlockEntity.MAX_OIL_COUNT 须与方块行为的 max_oil 一致
    private static final int DEFAULT_MAX_OIL = 256;

    private final int maxOil;

    public OilPotItemBehavior(Key blockId, int maxOil) {
        super(blockId);
        this.maxOil = maxOil;
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        // 手上的壶放下去后会被扣掉 耐久得先读出来
        Item held = context.getItem();
        int damage = held == null ? 0 : held.damage().orElse(0);
        BlockPos pos = context.getClickedPos();

        InteractionResult result = super.place(context);
        if (result != InteractionResult.SUCCESS) {
            return result;
        }

        BlockEntity blockEntity = context.getLevel().storageWorld().getBlockEntityAtIfLoaded(pos);
        if (blockEntity == null) {
            return result;
        }
        // get 的第二个参数数的是同类控制器里的第几个 不是控制器序号 所以恒为 0
        OilPotController controller = blockEntity.controller.get(OilPotController.class, 0);
        if (controller != null) {
            controller.setOilCount(this.maxOil - damage);
        }
        return result;
    }

    private static class Factory implements ItemBehaviorFactory<OilPotItemBehavior> {
        @Override
        public OilPotItemBehavior create(Pack pack, Path path, Key key, ConfigSection section) {
            int maxOil = BehaviorConfig.getInt(section, DEFAULT_MAX_OIL, "max_oil", "max-oil");
            ConfigValue blockValue = section.getNonNullValue("block", ConfigConstants.ARGUMENT_SECTION);
            if (blockValue.is(Map.class)) {
                BukkitBlockManager.instance().blockParser().addPendingConfigSection(
                        new PendingConfigSection(pack, path, key, blockValue.getAsSection()));
                return new OilPotItemBehavior(key, maxOil);
            }
            return new OilPotItemBehavior(blockValue.getAsIdentifier(), maxOil);
        }
    }
}
