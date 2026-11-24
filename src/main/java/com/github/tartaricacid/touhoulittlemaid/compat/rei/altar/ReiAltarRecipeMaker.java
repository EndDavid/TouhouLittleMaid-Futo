package com.github.tartaricacid.touhoulittlemaid.compat.rei.altar;

import com.github.tartaricacid.touhoulittlemaid.crafting.AltarRecipe;
import com.github.tartaricacid.touhoulittlemaid.init.InitRecipes;
import com.github.tartaricacid.touhoulittlemaid.util.JERIUtil;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;

import java.util.Arrays;
import java.util.List;

/**
 * 把配方管理器里所有 {@code touhou_little_maid:altar_crafting} 配方搬进 REI，
 * 移植自上游 {@code compat/rei/altar/ReiAltarRecipeMaker}。
 *
 * <p>数据来源是 {@code registry.getRecipeManager()}（REI 在客户端持有服务端同步下来的
 * 完整配方表），所以这里不需要自己读 JSON —— 也就不会出现「REI 里看到的和实际能合成的不一致」。</p>
 *
 * <p>祭坛配方最多 6 个原料槽，而 {@code AltarRecipe#getIngredients()} 固定返回 6 项
 * （没用到的位置是 {@link net.minecraft.world.item.crafting.Ingredient#EMPTY}）。
 * 画布上要画 6 个格子，所以这里只把非空的留下 ——
 * 于是「输入列表的长度」不再等于「槽位号」，这也是 {@code ReiAltarRecipeCategory}
 * 用 {@code getInput(inputs, index)} 逐格取、取不到就给空格子的原因。</p>
 */
public final class ReiAltarRecipeMaker {
    private ReiAltarRecipeMaker() {
    }

    public static void registerAltarRecipes(DisplayRegistry registry) {
        List<AltarRecipe> allRecipesFor = registry.getRecipeManager().getAllRecipesFor(InitRecipes.ALTAR_CRAFTING);
        JERIUtil.recipeWarp(allRecipesFor, (recipeId, inputs, output, powerCost, langKey) -> {
            List<EntryIngredient> inputEntries = inputs.stream()
                    .filter(it -> !it.isEmpty())
                    .map(ingredient -> EntryIngredient.of(
                            Arrays.stream(ingredient.getItems()).map(EntryStacks::of).toList()))
                    .toList();

            List<EntryIngredient> outputEntries = List.of(EntryIngredient.of(EntryStacks.of(output)));
            registry.add(new ReiAltarRecipeDisplay(recipeId, inputEntries, outputEntries, powerCost, langKey));
        });
    }
}

