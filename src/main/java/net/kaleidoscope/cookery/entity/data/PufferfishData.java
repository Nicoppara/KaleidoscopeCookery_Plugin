package net.kaleidoscope.cookery.entity.data;

import net.momirealms.craftengine.proxy.minecraft.network.syncher.EntityDataSerializersProxy;

// 河豚实体同步数据
public class PufferfishData<T> extends AbstractFishData<T> {
    // 触发父类初始化 保证 FromWater 数据项先于 PuffState 注册
    static {
        Object _unused = AbstractFishData.FromWater;
    }

    public static final PufferfishData<Integer> PuffState =
            new PufferfishData<>(PufferfishData.class, EntityDataSerializersProxy.INT, 0);

    protected PufferfishData(Class<?> clazz, Object serializer, T defaultValue) {
        super(clazz, serializer, defaultValue);
    }
}