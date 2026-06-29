package net.kaleidoscope.cookery.block.behavior;

import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelProxy;

import java.util.List;

public class StackedExtraDropBehavior extends BukkitBlockBehavior {
    public static final BlockBehaviorFactory<StackedExtraDropBehavior> FACTORY = new Factory();

    private final List<ExtraDrop> extraDrops;

    private StackedExtraDropBehavior(BlockDefinition blockDefinition, List<ExtraDrop> extraDrops) {
        super(blockDefinition);
        this.extraDrops = extraDrops;
    }

    @Override
    public void spawnAfterBreak(Object thisBlock, Object[] args) {
        if (extraDrops.isEmpty()) {
            return;
        }
        Object blockState = args[0];
        Object level = args[1];
        Object pos = args[2];

        ImmutableBlockState state = BlockStateUtils.getOptionalCustomBlockState(blockState).orElse(null);
        if (state == null) {
            return;
        }
        World world = BukkitAdaptor.adapt(LevelProxy.INSTANCE.getWorld(level));
        if (world == null) {
            return;
        }
        Vec3d dropPos = Vec3d.atCenterOf(LocationUtils.fromBlockPos(pos));
        for (ExtraDrop rule : extraDrops) {
            if (!rule.matches(state)) {
                continue;
            }
            Item item = InventoryUtils.createOrEmpty(rule.item);
            if (!ItemUtils.isEmpty(item)) {
                world.dropItemNaturally(dropPos, item.copyWithCount(rule.amount));
            }
        }
    }

    private static final class ExtraDrop {
        private final Property<?> property;
        private final String valueName;
        private final Key item;
        private final int amount;

        private ExtraDrop(Property<?> property, String valueName, Key item, int amount) {
            this.property = property;
            this.valueName = valueName;
            this.item = item;
            this.amount = amount;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private boolean matches(ImmutableBlockState state) {
            Comparable value = state.get((Property) property);
            return value != null && valueName.equals(((Property) property).valueName(value));
        }
    }

    private static class Factory implements BlockBehaviorFactory<StackedExtraDropBehavior> {
        private static final String[] EXTRA_DROPS = new String[] {"extra-drops", "extra_drops"};

        @Override
        public StackedExtraDropBehavior create(BlockDefinition block, ConfigSection section) {
            List<ExtraDrop> drops = section.getSectionList(EXTRA_DROPS, sub -> {
                String propName = sub.getString(new String[] {"property"}, (String) null);
                String value = sub.getString(new String[] {"value"}, (String) null);
                String itemId = sub.getString(new String[] {"item"}, (String) null);
                int amount = sub.getInt(new String[] {"amount"}, 1);
                if (propName == null || value == null || itemId == null) {
                    throw new IllegalArgumentException("stacked_extra_drop 规则缺少 property/value/item");
                }
                Property<?> property = block.getProperty(propName);
                if (property == null) {
                    throw new IllegalArgumentException("stacked_extra_drop: 方块不存在状态属性 " + propName);
                }
                return new ExtraDrop(property, value, Key.of(itemId), amount);
            });
            return new StackedExtraDropBehavior(block, drops);
        }
    }
}
