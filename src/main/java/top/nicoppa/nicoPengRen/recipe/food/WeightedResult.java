package top.nicoppa.nicoPengRen.recipe.food;

import net.momirealms.craftengine.core.util.Key;

/** 精准配方的一个带权重成品。weight 为相对权重，所有成品权重求和后计算（自适应概率） */
public record WeightedResult(Key key, int weight) {}
