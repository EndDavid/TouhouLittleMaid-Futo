package com.github.tartaricacid.touhoulittlemaid.util;

import com.github.tartaricacid.touhoulittlemaid.crafting.AltarRecipe;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.ItemEntityPlaceholder;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/**
 * 祭坛配方 → 配方查看器（REI）条目的公共转换逻辑，移植自上游 {@code util/JERIUtil}。
 *
 * <p><b>为什么要单独抽出来：</b>上游同时做了 JEI 与 REI 两套联动，两边的「配方怎么摊平成一排原料」
 * 逻辑完全一样，只是最终交给不同的注册表。所以上游把它放在这里，由
 * {@code compat/rei/altar/ReiAltarRecipeMaker} 调用。本移植版目前只接 REI，
 * 但这个类保持上游形状 —— 以后补 JEI 时不用再改一遍。</p>
 *
 * <h2>两个细节</h2>
 *
 * <ol>
 *     <li><b>非物品配方（生成实体）没有 {@code ItemStack} 结果</b>，
 *         所以统一换成模组自己的「生成？？？」占位物品，并把配方 id 写进它的 NBT ——
 *         那个物品的渲染器会按配方名去挑贴图。这与物品栏里看到的表现完全一致。</li>
 *     <li><b>语言键</b>：物品类配方统一用 {@code jei.<ns>.altar_craft.item_craft.result}，
 *         实体类配方用 {@code jei.<ns>.altar_craft.<配方文件名>.result}。
 *         这两串是上游已有的写法，{@code zh_cn.json} / {@code en_us.json} 里也已经有对应翻译。</li>
 * </ol>
 */
public final class JERIUtil {
    private JERIUtil() {
    }

    public static void recipeWarp(List<AltarRecipe> altarRecipes, AltarRecipeMaker maker) {
        for (AltarRecipe altarRecipe : altarRecipes) {
            ResourceLocation recipeId = altarRecipe.getId();
            ItemStack output = altarRecipe.getResultItem();
            if (!altarRecipe.isItemCraft()) {
                output = new ItemStack(InitItems.ENTITY_PLACEHOLDER);
                ItemEntityPlaceholder.setRecipeId(output, altarRecipe.getId());
            }
            String namespace = recipeId.getNamespace().toLowerCase(Locale.US);
            String langKey;
            if (altarRecipe.isItemCraft()) {
                langKey = String.format("jei.%s.altar_craft.%s.result", namespace, "item_craft");
            } else {
                Path path = Paths.get(recipeId.getPath().toLowerCase(Locale.US));
                langKey = String.format("jei.%s.altar_craft.%s.result", namespace, path.getFileName());
            }

            maker.accept(recipeId, altarRecipe.getIngredients(), output, altarRecipe.getPowerCost(), langKey);
        }
    }

    public interface AltarRecipeMaker {
        void accept(ResourceLocation recipeId, NonNullList<Ingredient> inputs, ItemStack output,
                    float powerCost, String langKey);
    }
}

