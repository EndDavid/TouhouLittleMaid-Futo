package com.github.tartaricacid.touhoulittlemaid.compat.patchouli;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import vazkii.patchouli.api.IComponentProcessor;
import vazkii.patchouli.api.IVariable;
import vazkii.patchouli.api.IVariableProvider;

/**
 * 手册里「祭坛合成」模板（{@code templates/altar_recipe.json}）的变量处理器。
 *
 * <p><b>本移植版是占位实现，不是上游逻辑的删减版：</b>上游这个处理器要读
 * {@code InitRecipes.ALTAR_CRAFTING} 与 {@code AltarRecipe}，而祭坛合成系统
 * 在本移植版里尚未移植（没有 {@code RecipeType} / {@code RecipeSerializer}，
 * 也没有任何 {@code data/.../recipes/altar/*.json}）。因此这里只保留
 * <b>同一套变量名</b>，并统一返回空值：</p>
 *
 * <ul>
 *     <li>手册不会因为 {@code ClassNotFoundException} 整页加载失败；</li>
 *     <li>页面仍然会画出六个输入槽、能量图标与输出槽的框架，只是里面为空；</li>
 *     <li>等祭坛系统移植完成后，把 {@link #setup} / {@link #process} 换成上游实现即可，
 *         模板 JSON 与页面按键都不需要改。</li>
 * </ul>
 *
 * <p>上游变量名：{@code recipe_id}（输入）、{@code input1~6}、
 * {@code power_cost}、{@code output_item}、{@code output_entity}、{@code output_desc}。</p>
 */
public class AltarRecipeComponent implements IComponentProcessor {
    private static final String INPUT = "input";
    private static final String POWER_COST = "power_cost";
    private static final String OUTPUT_ITEM = "output_item";
    private static final String OUTPUT_ENTITY = "output_entity";
    private static final String OUTPUT_DESC = "output_desc";

    private static boolean warned = false;

    @Override
    public void setup(IVariableProvider variables) {
        if (!warned) {
            warned = true;
            // 只提示一次：手册里引用本模板的页面有好几页，逐页刷屏没有意义
            TouhouLittleMaid.LOGGER.warn("[Futo] Patchouli altar recipe pages are placeholders: " +
                                        "the altar crafting system has not been ported yet.");
        }
    }

    @Nullable
    @Override
    public IVariable process(String key) {
        if (key.startsWith(INPUT)) {
            // input1 ~ input6：全部返回空物品，槽位会保持空框
            return IVariable.from(ItemStack.EMPTY);
        }
        switch (key) {
            case POWER_COST:
                return IVariable.wrap("");
            case OUTPUT_ITEM:
                return IVariable.from(ItemStack.EMPTY);
            case OUTPUT_ENTITY:
                // 与上游的兜底值一致：实体页在找不到实体时就画物品
                return IVariable.wrap("minecraft:item");
            case OUTPUT_DESC:
                return IVariable.wrap("");
            default:
                return null;
        }
    }
}

