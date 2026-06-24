package net.kaleidoscope.cookery.plugin;

import net.momirealms.craftengine.core.block.behavior.BlockBehavior;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorType;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorFactory;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorTemplate;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorType;
import net.momirealms.craftengine.core.item.behavior.ItemBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorType;
import net.momirealms.craftengine.core.registry.BuiltInRegistries;
import net.momirealms.craftengine.core.registry.Registries;
import net.momirealms.craftengine.core.registry.WritableRegistry;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.ResourceKey;

// 行为注册工具
public final class RegistryUtils {
    private RegistryUtils() {}

    public static <T extends BlockBehavior> BlockBehaviorType<T> registerBlockBehavior(Key id, BlockBehaviorFactory<T> factory) {
        BlockBehaviorType<T> type = new BlockBehaviorType<>(id, factory);
        ((WritableRegistry<BlockBehaviorType<? extends BlockBehavior>>) BuiltInRegistries.BLOCK_BEHAVIOR_TYPE)
                .register(ResourceKey.create(Registries.BLOCK_BEHAVIOR_TYPE.location(), id), type);
        return type;
    }

    public static <T extends ItemBehavior> ItemBehaviorType<T> registerItemBehavior(Key id, ItemBehaviorFactory<T> factory) {
        ItemBehaviorType<T> type = new ItemBehaviorType<>(id, factory);
        ((WritableRegistry<ItemBehaviorType<? extends ItemBehavior>>) BuiltInRegistries.ITEM_BEHAVIOR_TYPE)
                .register(ResourceKey.create(Registries.ITEM_BEHAVIOR_TYPE.location(), id), type);
        return type;
    }

    public static <T extends FurnitureBehaviorTemplate> FurnitureBehaviorType<T> registerFurnitureBehavior(Key id, FurnitureBehaviorFactory<T> factory) {
        FurnitureBehaviorType<T> type = new FurnitureBehaviorType<>(id, factory);
        ((WritableRegistry<FurnitureBehaviorType<? extends FurnitureBehaviorTemplate>>) BuiltInRegistries.FURNITURE_BEHAVIOR_TYPE)
                .register(ResourceKey.create(Registries.FURNITURE_BEHAVIOR_TYPE.location(), id), type);
        return type;
    }
}