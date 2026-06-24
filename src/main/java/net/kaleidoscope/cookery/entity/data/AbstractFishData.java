package net.kaleidoscope.cookery.entity.data;

import net.momirealms.craftengine.bukkit.entity.data.PathfinderMobData;
import net.momirealms.craftengine.proxy.minecraft.network.syncher.EntityDataSerializersProxy;

// 鱼类实体同步数据基类
public class AbstractFishData<T> extends PathfinderMobData<T> {
    public static final AbstractFishData<Boolean> FromWater =
            new AbstractFishData<>(AbstractFishData.class, EntityDataSerializersProxy.BOOLEAN, false);

    protected AbstractFishData(Class<?> clazz, Object serializer, T defaultValue) {
        super(clazz, serializer, defaultValue);
    }
}