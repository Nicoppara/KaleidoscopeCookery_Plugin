package net.kaleidoscope.cookery.block.behavior;

import net.kaleidoscope.cookery.block.entity.StoveController;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
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
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.util.Hands;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.item.ItemMatch;
import net.kaleidoscope.cookery.item.KitchenShovel;

public class StoveBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<StoveBehavior> FACTORY = new Factory();

    private static final Key[] SHOVELS = {
            ItemKeys.WOODEN_SHOVEL, ItemKeys.STONE_SHOVEL, ItemKeys.IRON_SHOVEL,
            ItemKeys.GOLDEN_SHOVEL, ItemKeys.DIAMOND_SHOVEL, ItemKeys.NETHERITE_SHOVEL
    };

    private static final Key IGNITE_FIRE_CHARGE_SOUND = Key.of("minecraft:entity.firework_rocket.blast");
    private static final Key IGNITE_FLINT_SOUND = Key.of("minecraft:item.flintandsteel.use");
    private static final Key EXTINGUISH_SOUND = Key.of("minecraft:block.fire.extinguish");
    private static final float SOUND_VOLUME = 1.0f;
    private static final float SOUND_PITCH = 1.0f;

    public Key kitchenShovelItem = ItemKeys.KITCHEN_SHOVEL;
    public Key kitchenShovelOilModel = ItemKeys.KITCHEN_SHOVEL_OIL_MODEL;
    public int particleInterval = 20;
    public int particleCount = 3;

    private Property<Boolean> litProperty;
    private Property<Direction> facingProperty;
    private int controllerId;

    public StoveBehavior(BlockDefinition blockDefinition, Property<Boolean> litProperty) {
        super(blockDefinition);
        this.litProperty = litProperty;
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new StoveController(blockEntity, this);
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    public Property<Boolean> getLitProperty() {
        return litProperty;
    }

    public Property<Direction> getFacingProperty() {
        return facingProperty;
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!InteractGuard.canInteract(player, context.getLevel(), context.getClickedPos())) {
            return InteractionResult.PASS;
        }
        // 只处理主手那次调用 避免主副手各触发一次
        if (context.getHand() == InteractionHand.OFF_HAND) {
            return InteractionResult.PASS;
        }

        // 点火 打火石/火焰弹 工具副手优先
        InteractionHand igniteHand = Hands.toolHand(player, this::isIgniteItem);
        Item igniteItem = player.getItemInHand(igniteHand);
        if (isIgniteItem(igniteItem)) {
            return handleIgnite(context, state, player, igniteHand, igniteItem);
        }

        // 熄火 铲子 工具副手优先
        if (state.get(litProperty)) {
            InteractionHand extHand = Hands.toolHand(player, this::isExtinguishItem);
            Item extItem = player.getItemInHand(extHand);
            if (isExtinguishItem(extItem)) {
                return handleExtinguish(context, state, player, extHand);
            }
        }
        return InteractionResult.PASS;
    }

    private InteractionResult handleIgnite(UseOnContext context, ImmutableBlockState state, Player player,
                                          InteractionHand hand, Item igniteItem) {
        boolean newLit = !state.get(litProperty);
        ImmutableBlockState newState = state.with(litProperty, newLit);

        LevelWriterProxy.INSTANCE.setBlock(
                context.getLevel().minecraftWorld(),
                LocationUtils.toBlockPos(context.getClickedPos()),
                newState.customBlockState().minecraftState(),
                3
        );

        boolean fireCharge = ItemMatch.is(igniteItem, ItemKeys.FIRE_CHARGE);
        if (newLit) {
            context.getLevel().playBlockSound(
                    new Vec3d(context.getClickedPos().x() + 0.5, context.getClickedPos().y() + 0.5, context.getClickedPos().z() + 0.5),
                    fireCharge ? IGNITE_FIRE_CHARGE_SOUND : IGNITE_FLINT_SOUND,
                    SOUND_VOLUME, SOUND_PITCH
            );
            // 火焰弹点火消耗一个 创造不扣
            if (fireCharge) {
                InventoryUtils.shrinkHeld(player, igniteItem, 1);
            }
        } else {
            context.getLevel().playBlockSound(
                    new Vec3d(context.getClickedPos().x() + 0.5, context.getClickedPos().y() + 0.5, context.getClickedPos().z() + 0.5),
                    Key.of("minecraft:block.fire.extinguish"),
                    1.0f, 1.0f
            );
        }

        player.swingHand(hand);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private InteractionResult handleExtinguish(UseOnContext context, ImmutableBlockState state, Player player, InteractionHand hand) {
        ImmutableBlockState newState = state.with(litProperty, false);
        LevelWriterProxy.INSTANCE.setBlock(
                context.getLevel().minecraftWorld(),
                LocationUtils.toBlockPos(context.getClickedPos()),
                newState.customBlockState().minecraftState(),
                3
        );
        context.getLevel().playBlockSound(
                new Vec3d(context.getClickedPos().x() + 0.5, context.getClickedPos().y() + 0.5, context.getClickedPos().z() + 0.5),
                EXTINGUISH_SOUND,
                SOUND_VOLUME, SOUND_PITCH
        );
        player.swingHand(hand);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private boolean isIgniteItem(Item item) {
        return ItemMatch.is(item, ItemKeys.FLINT_AND_STEEL) || ItemMatch.is(item, ItemKeys.FIRE_CHARGE);
    }

    @Override
    public void handlePrecipitation(Object thisBlock, Object[] args) {
        Object blockState = args[0];
        Object level = args[1];
        Object pos = args[2];
        Object precipitation = args[3];

        var optionalCustomState = BlockStateUtils.getOptionalCustomBlockState(blockState);
        if (optionalCustomState.isEmpty()) {
            return;
        }

        ImmutableBlockState state = optionalCustomState.get();
        if (!state.get(litProperty)) {
            return;
        }

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
        if (optionalCustomState.isEmpty()) {
            return;
        }

        ImmutableBlockState state = optionalCustomState.get();
        if (!state.get(litProperty)) {
            return;
        }

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
        // 沾了油的锅铲拍不灭火
        return KitchenShovel.is(item, kitchenShovelItem)
                && !KitchenShovel.hasOil(item, kitchenShovelOilModel);
    }

    private static class Factory implements BlockBehaviorFactory<StoveBehavior> {
        @Override
        public StoveBehavior create(BlockDefinition block, ConfigSection section) {
            StoveBehavior behavior = new StoveBehavior(
                    block,
                    BlockBehaviorFactory.getProperty(section.path(), block, "lit", Boolean.class)
            );
            // facing 用于决定火焰贴在哪一面 可能不存在 缺失时火焰落在中心
            behavior.facingProperty = BlockBehaviorFactory.getOptionalProperty(block, "facing", Direction.class);
            behavior.kitchenShovelItem = Key.of(BehaviorConfig.getString(section, behavior.kitchenShovelItem.asString(), "extinguish_kitchen_shovel_item", "extinguish-kitchen-shovel-item"));
            behavior.kitchenShovelOilModel = Key.of(BehaviorConfig.getString(section, behavior.kitchenShovelOilModel.asString(), "shovel_oil_model", "shovel-oil-model"));
            behavior.particleInterval = BehaviorConfig.getInt(section, behavior.particleInterval, "particle_interval", "particle-interval");
            behavior.particleCount = BehaviorConfig.getInt(section, behavior.particleCount, "particle_count", "particle-count");
            return behavior;
        }
    }
}
