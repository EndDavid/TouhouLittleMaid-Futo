package com.github.tartaricacid.touhoulittlemaid.init;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.recipe.MemorizableGensokyoRecipe;
import net.minecraft.core.Registry;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleRecipeSerializer;

/**
 * 合成表序列化器注册表，目前只有帕秋莉手册那一张（对应上游
 * {@code init/InitRecipes} 里与手册相关的那一项）。
 *
 * <p>上游还在这里注册了祭坛合成的 {@code RecipeType} 与 {@code RecipeSerializer}，
 * 那部分依赖尚未移植的祭坛系统，等搬过来再补。</p>
 *
 * <p>序列化器本身与帕秋莉无关，所以无条件注册：数据包里的配方文件由
 * {@code fabric:load_conditions} 负责「没装帕秋莉就不加载」。</p>
 */
public final class InitRecipes {
    /**
     * 手册配方的序列化器。配方类在构造时自己给出材料与结果，
     * 因此用原版的 {@link SimpleRecipeSerializer} 即可，不需要网络同步字段。
     */
    public static RecipeSerializer<MemorizableGensokyoRecipe> MEMORIZABLE_GENSOKYO;

    private InitRecipes() {
    }

    public static void init() {
        MEMORIZABLE_GENSOKYO = Registry.register(Registry.RECIPE_SERIALIZER,
                TouhouLittleMaid.rl("memorizable_gensokyo"),
                new SimpleRecipeSerializer<>(MemorizableGensokyoRecipe::new));
    }
}

