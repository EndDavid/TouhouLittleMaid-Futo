package com.github.tartaricacid.touhoulittlemaid.compat.rei.altar;

import com.github.tartaricacid.touhoulittlemaid.compat.rei.MaidREIClientPlugin;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * REI 里「一条祭坛配方」的数据载体，移植自上游 {@code compat/rei/altar/ReiAltarRecipeDisplay}。
 *
 * <p>REI 的分类只认 {@link Display} 这两张表：{@code getInputEntries()} 与
 * {@code getOutputEntries()}，每一项都是一个 {@code EntryIngredient}
 * （= 「这一格可以是这些物品中的任意一个」）。画布怎么摆由
 * {@link ReiAltarRecipeCategory#setupDisplay} 决定，所以这个类只管数据。</p>
 *
 * <p>{@code powerCost} 与 {@code langKey} 不是 REI 的字段，是模组自己的：
 * 前者画在配方中间（祭坛要消耗多少 P），后者用来查「结果是什么」那一行文案。</p>
 */
public class ReiAltarRecipeDisplay implements Display {
    private final ResourceLocation id;
    private final List<EntryIngredient> inputs;
    private final List<EntryIngredient> outputs;
    private final float powerCost;
    private final String langKey;

    public ReiAltarRecipeDisplay(ResourceLocation id, List<EntryIngredient> inputs,
                                 List<EntryIngredient> outputs, float powerCost, String langKey) {
        this.id = id;
        this.inputs = inputs;
        this.outputs = outputs;
        this.powerCost = powerCost;
        this.langKey = langKey;
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        return inputs;
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        return outputs;
    }

    public ResourceLocation getId() {
        return id;
    }

    public float getPowerCost() {
        return powerCost;
    }

    public String getLangKey() {
        return langKey;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return MaidREIClientPlugin.ALTAR;
    }

    /**
     * 让 REI 记住「这条显示来自哪个配方文件」，配方书 / 收藏之类的功能要靠它。
     */
    @Override
    public Optional<ResourceLocation> getDisplayLocation() {
        return Optional.of(id);
    }
}

