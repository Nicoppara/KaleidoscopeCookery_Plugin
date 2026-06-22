package top.nicoppa.nicoPengRen.common.entity;

import net.momirealms.craftengine.proxy.minecraft.network.syncher.EntityDataSerializersProxy;

/**
 * 河豚实体同步数据 在 {@link AbstractFishData} 基础上追加膨胀形态（小/中/大）数据项
 */
public class PufferfishData<T> extends AbstractFishData<T> {
    static {
        Object _unused = AbstractFishData.FromWater;
    }

    public static final PufferfishData<Integer> PuffState =
            new PufferfishData<>(PufferfishData.class, EntityDataSerializersProxy.INT, 0);

    protected PufferfishData(Class<?> clazz, Object serializer, T defaultValue) {
        super(clazz, serializer, defaultValue);
    }
}