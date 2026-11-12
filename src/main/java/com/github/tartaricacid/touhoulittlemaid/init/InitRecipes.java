package com.github.tartaricacid.touhoulittlemaid.init;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.crafting.AltarRecipe;
import com.github.tartaricacid.touhoulittlemaid.crafting.AltarRecipeSerializer;
import com.github.tartaricacid.touhoulittlemaid.recipe.MemorizableGensokyoRecipe;
import net.minecraft.core.Registry;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SimpleRecipeSerializer;

/**
 * 合成表注册表，移植自上游 {@code init/InitRecipes}。
 *
 * <p>目前有两项：</p>
 * <ol>
 *     <li><b>祭坛合成</b>（{@code touhou_little_maid:altar_crafting}）：
 *         {@link RecipeType} + {@link AltarRecipeSerializer}，数据包里的配方在
 *         {@code data/touhou_little_maid/recipes/altar/*.json}；</li>
 *     <li><b>帕秋莉手册配方</b>（{@code touhou_little_maid:memorizable_gensokyo}）：
 *         与手册本身无关，所以无条件注册；没有帕秋莉时由数据包的
 *         {@code fabric:load_conditions} 决定不加载那张配方。</li>
 * </ol>
 *
 * <p>注册顺序没有依赖：{@link AltarRecipe} 在构造时才读这两个字段，而
 * 数据包解析发生在注册之后。但 {@link #init()} 必须早于资源 / 数据包加载，
 * 因此仍在主入口里显式调用一次。</p>
 */
public final class InitRecipes {
    /**
     * 祭坛合成的配方类型。
     *
     * <p>1.18.2 的 {@code RecipeType.register(String)} 会把参数直接当完整 id 用
     * （内部 {@code new ResourceLocation(参数)}），所以这里带命名空间。</p>
     */
    public static RecipeType<AltarRecipe> ALTAR_CRAFTING;

    /**
     * 祭坛合成表的序列化器。
     */
    public static RecipeSerializer<AltarRecipe> ALTAR_RECIPE_SERIALIZER;

    /**
     * 手册配方的序列化器。配方类在构造时自己给出材料与结果，
     * 因此用原版的 {@link SimpleRecipeSerializer} 即可，不需要网络同步字段。
     */
    public static RecipeSerializer<MemorizableGensokyoRecipe> MEMORIZABLE_GENSOKYO;

    private InitRecipes() {
    }

    public static void init() {
        ALTAR_CRAFTING = RecipeType.register(TouhouLittleMaid.MOD_ID + ":altar_crafting");
        ALTAR_RECIPE_SERIALIZER = Registry.register(Registry.RECIPE_SERIALIZER,
                TouhouLittleMaid.rl("altar_crafting"), new AltarRecipeSerializer());

        MEMORIZABLE_GENSOKYO = Registry.register(Registry.RECIPE_SERIALIZER,
                TouhouLittleMaid.rl("memorizable_gensokyo"),
                new SimpleRecipeSerializer<>(MemorizableGensokyoRecipe::new));
    }
}

