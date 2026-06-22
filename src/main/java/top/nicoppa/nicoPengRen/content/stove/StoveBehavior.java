package top.nicoppa.nicoPengRen.content.stove;

import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelWriterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.state.BlockBehaviourProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.material.FluidStateProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.material.FluidsProxy;
import top.nicoppa.nicoPengRen.common.config.BehaviorConfig;
import top.nicoppa.nicoPengRen.common.item.ItemMatch;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 炉灶方块行为 处理点火（打火石/火焰弹）与熄火（铲子、降雨、上方水流），切换 lit 状态
 *
 * 可在 behaviors 配置里覆盖（默认值即下方所示，键名支持下划线/连字符）：
 * behaviors:
 *   - type: nicopengren:stove
 *     extinguish_kitchen_shovel_item: "custom:kitchen_shovel_no_oil"  # 可熄火的自定义厨铲（原版各种铲子始终可熄火）
 */
public class StoveBehavior extends BukkitBlockBehavior {
    public static final BlockBehaviorFactory<StoveBehavior> FACTORY = new Factory();

    // TODO 这里应该写个tag，自定义那里可以写个类，提出来再改
    private static final Key WOODEN_SHOVEL = Key.of("minecraft:wooden_shovel");
    private static final Key STONE_SHOVEL = Key.of("minecraft:stone_shovel");
    private static final Key IRON_SHOVEL = Key.of("minecraft:iron_shovel");
    private static final Key GOLDEN_SHOVEL = Key.of("minecraft:golden_shovel");
    private static final Key DIAMOND_SHOVEL = Key.of("minecraft:diamond_shovel");
    private static final Key NETHERITE_SHOVEL = Key.of("minecraft:netherite_shovel");
    private static final Key[] SHOVELS = new Key[] {
            WOODEN_SHOVEL, STONE_SHOVEL, IRON_SHOVEL, GOLDEN_SHOVEL, DIAMOND_SHOVEL, NETHERITE_SHOVEL
    };

    public Key kitchenShovelNoOilItem = Key.of("custom:kitchen_shovel_no_oil");

    private Property<Boolean> litProperty;

    public StoveBehavior(BlockDefinition blockDefinition, Property<Boolean> litProperty) {
        super(blockDefinition);
        this.litProperty = litProperty;
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = (Player) context.getPlayer().platformPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        Item craftItem = context.getItem();

        // 打火石/火焰弹：切换点火状态
        if (heldItem.getType() == Material.FLINT_AND_STEEL || heldItem.getType() == Material.FIRE_CHARGE) {
            boolean newLit = !state.get(litProperty);
            ImmutableBlockState newState = state.with(litProperty, newLit);

            LevelWriterProxy.INSTANCE.setBlock(
                    context.getLevel().minecraftWorld(),
                    LocationUtils.toBlockPos(context.getClickedPos()),
                    newState.customBlockState().minecraftState(),
                    3
            );

            if (newLit) {
                // TODO 音效这里可以拆出去，再说
                context.getLevel().playBlockSound(
                        new Vec3d(context.getClickedPos().x() + 0.5, context.getClickedPos().y() + 0.5, context.getClickedPos().z() + 0.5),
                        heldItem.getType() == Material.FIRE_CHARGE
                                ? Key.of("minecraft:entity.firework_rocket.blast")
                                : Key.of("minecraft:item.flintandsteel.use"),
                        1.0f, 1.0f
                );
                if (heldItem.getType() == Material.FIRE_CHARGE) {
                    heldItem.setAmount(heldItem.getAmount() - 1);
                }
            } else {
                context.getLevel().playBlockSound(
                        new Vec3d(context.getClickedPos().x() + 0.5, context.getClickedPos().y() + 0.5, context.getClickedPos().z() + 0.5),
                        Key.of("minecraft:block.fire.extinguish"),
                        1.0f, 1.0f
                );
            }

            player.swingMainHand();
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 熄火
        if (state.get(litProperty) && !craftItem.isEmpty()) {
            if (isExtinguishItem(craftItem)) {
                ImmutableBlockState newState = state.with(litProperty, false);
                LevelWriterProxy.INSTANCE.setBlock(
                        context.getLevel().minecraftWorld(),
                        LocationUtils.toBlockPos(context.getClickedPos()),
                        newState.customBlockState().minecraftState(),
                        3
                );
                context.getLevel().playBlockSound(
                        new Vec3d(context.getClickedPos().x() + 0.5, context.getClickedPos().y() + 0.5, context.getClickedPos().z() + 0.5),
                        Key.of("minecraft:block.fire.extinguish"),
                        1.0f, 1.0f
                );
                player.swingMainHand();
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public void handlePrecipitation(Object thisBlock, Object[] args) {
        Object blockState = args[0];
        Object level = args[1];
        Object pos = args[2];
        Object precipitation = args[3];

        var optionalCustomState = BlockStateUtils.getOptionalCustomBlockState(blockState);
        if (optionalCustomState.isEmpty()) return;

        ImmutableBlockState state = optionalCustomState.get();
        if (!state.get(litProperty)) return;


        if (precipitation != null && !precipitation.toString().equals("NONE")) {
            extinguishStove(level, pos, state);
        }
    }

    @Override
    public void neighborChanged(Object thisBlock, Object[] args) {
        Object blockState = args[0];
        Object level = args[1];
        Object blockPos = args[2];

        var optionalCustomState = BlockStateUtils.getOptionalCustomBlockState(blockState);
        if (optionalCustomState.isEmpty()) return;

        ImmutableBlockState state = optionalCustomState.get();
        if (!state.get(litProperty)) return;

        Object abovePos = LocationUtils.above(blockPos);
        Object aboveBlockState = BlockGetterProxy.INSTANCE.getBlockState(level, abovePos);
        if (aboveBlockState != null) {
            Object fluidState = BlockBehaviourProxy.BlockStateBaseProxy.INSTANCE.getFluidState(aboveBlockState);
            if (fluidState != null) {
                Object fluidType = FluidStateProxy.INSTANCE.getType(fluidState);
                if (fluidType == FluidsProxy.WATER || fluidType == FluidsProxy.FLOWING_WATER) {
                    extinguishStove(level, blockPos, state);
                }
            }
        }
    }

    private void extinguishStove(Object level, Object pos, ImmutableBlockState state) {
        ImmutableBlockState newState = state.with(litProperty, false);
        LevelWriterProxy.INSTANCE.setBlock(
                level,
                pos,
                newState.customBlockState().minecraftState(),
                3
        );
    }

    private boolean isExtinguishItem(Item item) {
        Key itemId = item.id();
        for (Key shovel : SHOVELS) {
            if (shovel.equals(itemId)) {
                return true;
            }
        }
        return ItemMatch.is(item, kitchenShovelNoOilItem);
    }

    private static class Factory implements BlockBehaviorFactory<StoveBehavior> {
        @Override
        public StoveBehavior create(BlockDefinition block, ConfigSection section) {
            StoveBehavior behavior = new StoveBehavior(
                    block,
                    BlockBehaviorFactory.getProperty(section.path(), block, "lit", Boolean.class)
            );
            behavior.kitchenShovelNoOilItem = Key.of(BehaviorConfig.getString(section, behavior.kitchenShovelNoOilItem.toString(), "extinguish_kitchen_shovel_item", "extinguish-kitchen-shovel-item"));
            return behavior;
        }
    }
}
