package com.github.tartaricacid.touhoulittlemaid.compat.patchouli;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.crafting.AltarRecipe;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitRecipes;
import com.google.common.collect.Lists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import vazkii.patchouli.api.IComponentProcessor;
import vazkii.patchouli.api.IVariable;
import vazkii.patchouli.api.IVariableProvider;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/**
 * 手册里「祭坛合成」模板（{@code templates/altar_recipe.json}）的变量处理器。
 *
 * <p>页面 JSON 只写一个 {@code recipe_id}，其余全部由这里算出来；
 * 逐字移植自上游 {@code compat/patchouli/AltarRecipeComponent}。</p>
 *
 * <p>上游变量名：{@code recipe_id}（输入）、{@code input1~6}、
 * {@code power_cost}、{@code output_item}、{@code output_entity}、{@code output_desc}。</p>
 *
 * <h2>每个变量怎么算</h2>
 * <table border="1">
 *     <caption>变量表</caption>
 *     <tr><th>变量</th><th>含义</th></tr>
 *     <tr><td>{@code input1..input6}</td><td>第 N 个材料 —— 该 {@link Ingredient}
 *         接受的全部物品 id，逗号分隔（Patchouli 会画成可循环切换的物品槽）</td></tr>
 *     <tr><td>{@code power_cost}</td><td>{@code x1.50} 形式的灵力消耗</td></tr>
 *     <tr><td>{@code output_item}</td><td>物品产物（实体产物时为空，只留实体槽）</td></tr>
 *     <tr><td>{@code output_entity}</td><td>实体产物的注册名；找不到时兜底
 *         {@code minecraft:item}，页面至少还能画出物品槽</td></tr>
 *     <tr><td>{@code output_desc}</td><td>产物的语言键
 *         {@code jei.<ns>.altar_craft.<配方文件名>.result}</td></tr>
 * </table>
 *
 * <h2>与上游的差异</h2>
 * <p>上游的 {@code InitEntities.BOX} / {@code InitEntities.MAID} 是 {@code RegistryObject}，
 * 这里两个都是直接的 {@code EntityType} 字段，所以 {@code get()} 去掉，其余逐字一致。</p>
 */
public class AltarRecipeComponent implements IComponentProcessor {
    private static final String RECIPE_ID = "recipe_id";
    private static final String INPUT = "input";
    private static final String POWER_COST = "power_cost";
    private static final String OUTPUT_ITEM = "output_item";
    private static final String OUTPUT_ENTITY = "output_entity";
    private static final String OUTPUT_DESC = "output_desc";

    @Nullable
    private AltarRecipe recipe;

    @Override
    public void setup(IVariableProvider variables) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        ResourceLocation recipeId = new ResourceLocation(variables.get(RECIPE_ID).asString());
        List<AltarRecipe> allAltarRecipes = level.getRecipeManager().getAllRecipesFor(InitRecipes.ALTAR_CRAFTING);
        for (AltarRecipe altarRecipe : allAltarRecipes) {
            if (altarRecipe.getId().equals(recipeId)) {
                this.recipe = altarRecipe;
                return;
            }
        }
        // 兜底：拿一条空配方，让下面的 process 都能安全跑完（页面画成空框）
        this.recipe = new AltarRecipe(new ResourceLocation(TouhouLittleMaid.MOD_ID, "empty"),
                EntityType.ITEM, null, 0, Ingredient.EMPTY);
        TouhouLittleMaid.LOGGER.error("Altar recipe not found: {}", recipeId);
    }

    @Nullable
    @Override
    public IVariable process(String key) {
        if (this.recipe == null) {
            return key.startsWith(INPUT) ? IVariable.from(ItemStack.EMPTY) : null;
        }
        if (key.startsWith(INPUT)) {
            int index = Integer.parseInt(key.substring(INPUT.length())) - 1;
            if (index < 0 || index >= this.recipe.getIngredients().size()) {
                return IVariable.from(ItemStack.EMPTY);
            }
            Ingredient ingredient = this.recipe.getIngredients().get(index);
            ItemStack[] stacks = ingredient.getItems();
            if (stacks.length == 0) {
                return IVariable.from(ItemStack.EMPTY);
            }
            // 一个 Ingredient 可以接受多种物品（tag），全部列出来交给 Patchouli 循环显示
            List<String> stackNames = Lists.newArrayList();
            for (ItemStack stack : stacks) {
                ResourceLocation itemId = Registry.ITEM.getKey(stack.getItem());
                if (itemId != null) {
                    stackNames.add(itemId.toString());
                }
            }
            return IVariable.wrap(StringUtils.join(stackNames, ","));
        }

        switch (key) {
            case POWER_COST -> {
                return IVariable.wrap(String.format("x%.2f", this.recipe.getPowerCost()));
            }
            case OUTPUT_ITEM -> {
                return IVariable.from(this.recipe.getResultItem());
            }
            case OUTPUT_DESC -> {
                String namespace = this.recipe.getId().getNamespace().toLowerCase(Locale.US);
                String langKey;
                if (this.recipe.isItemCraft()) {
                    langKey = String.format("jei.%s.altar_craft.%s.result", namespace, "item_craft");
                } else {
                    // 实体产物按配方文件名区分（reborn_maid / spawn_box …）
                    Path path = Paths.get(this.recipe.getId().getPath().toLowerCase(Locale.US));
                    langKey = String.format("jei.%s.altar_craft.%s.result", namespace, path.getFileName());
                }
                return IVariable.wrap(I18n.get(langKey));
            }
            case OUTPUT_ENTITY -> {
                EntityType<?> entityType = this.recipe.getEntityType();
                // 特判：spawn_box 的产物是「装着一只女仆的盒子」，直接把盒子画出来是个空箱子，
                // 所以手册里改画女仆 —— 上游 compat/patchouli/AltarRecipeComponent 同款分支。
                if (entityType.equals(InitEntities.BOX)) {
                    entityType = InitEntities.MAID;
                }
                ResourceLocation entityId = Registry.ENTITY_TYPE.getKey(entityType);
                if (entityId != null) {
                    return IVariable.wrap(entityId.toString());
                }
                return IVariable.wrap("minecraft:item");
            }
            default -> {
                return null;
            }
        }
    }
}
