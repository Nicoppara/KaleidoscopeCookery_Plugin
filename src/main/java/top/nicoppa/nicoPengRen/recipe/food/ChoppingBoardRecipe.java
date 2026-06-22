package top.nicoppa.nicoPengRen.recipe.food;

import net.momirealms.craftengine.core.util.Key;

import java.util.List;

/**
 * 砧板配方 {@code input} 为原料(require)    {@code stage} 为阶段数(需要用菜刀切的次数)
 * {@code values} 为各阶段展示模型(放下values[0]，每切一刀阶段+1)；{@code results} 为产物
 */
public record ChoppingBoardRecipe(
        Key id,
        Key input,
        int stage,
        List<String> values,
        List<ChoppingResult> results
) {}
