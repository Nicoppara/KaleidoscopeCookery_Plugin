package top.nicoppa.nicoPengRen.recipe.food;

import net.momirealms.craftengine.core.util.Key;
import top.nicoppa.nicoPengRen.recipe.ApplianceType;

import java.util.List;

/**
 * 精准配方 1 对 1，但成品可为带权重的列表，按权重随机产出一个
 * {@code id} 为配方名，{@code input} 为原料(require)，{@code results} 为加权成品列表
 * 扁平写法时为单个、权重 100%
 */
public record AccurateFoodRecipe(
        Key id,
        Key input,
        List<WeightedResult> results,
        ApplianceType cook,
        List<String> lore
) {
    // 用于展示/记录的代表性成品（取列表首个）
    public Key primaryResult() {
        return results.isEmpty() ? null : results.get(0).key();
    }
}
