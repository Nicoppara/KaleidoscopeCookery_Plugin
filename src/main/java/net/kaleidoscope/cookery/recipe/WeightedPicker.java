package net.kaleidoscope.cookery.recipe;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;

// 配方产物的权重抽取 精确配方与砧板共用
public final class WeightedPicker {
    // 不写权重时的默认值 等价于必中
    public static final int FULL_WEIGHT = 100;

    private WeightedPicker() {}

    // 权重当百分比 独立判定一次是否命中 大于等于 100 必中
    public static boolean roll(int weight) {
        if (weight <= 0) {
            return false;
        }
        return weight >= FULL_WEIGHT || ThreadLocalRandom.current().nextInt(FULL_WEIGHT) < weight;
    }

    // 按相对权重随机选一个 全 0 权重退回首个 空列表返回 null
    // 单产物配方占绝大多数 特化掉求和与游走
    public static <T> T pick(List<T> entries, ToIntFunction<T> weight) {
        int size = entries.size();
        if (size == 0) {
            return null;
        }
        if (size == 1) {
            return entries.getFirst();
        }
        int total = 0;
        for (T entry : entries) {
            int w = weight.applyAsInt(entry);
            if (w > 0) {
                total += w;
            }
        }
        if (total <= 0) {
            return entries.getFirst();
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (T entry : entries) {
            int w = weight.applyAsInt(entry);
            if (w <= 0) {
                continue;
            }
            if (roll < w) {
                return entry;
            }
            roll -= w;
        }
        return entries.getLast();
    }
}
