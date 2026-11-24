package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.crafting.AltarRecipe;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitRecipes;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * 「生成？？？」占位物品，移植自上游 {@code item/ItemEntityPlaceholder}。
 *
 * <p>三件进度（{@code base/spawn_maid}、{@code maid_base/reborn_maid}、
 * {@code challenge/spawn_lightning_bolt}）的图标要画的是「祭坛这条配方做出来的东西」，
 * 而它可能根本不是一个物品（女仆、闪电……）。上游的解法是注册这么一件空壳物品，
 * 把配方 id 记在 {@code RecipeId} 上，由客户端渲染器按配方名去换贴图：
 * {@code textures/item/<配方文件名>.png}。</p>
 *
 * <h2>它同时也是能用的物品</h2>
 * <p>对着<b>方块顶面</b>右键，会按 {@code RecipeId} 找到那条祭坛配方并直接生成产物
 * （{@code AltarRecipe#spawnOutputEntity}），然后消耗掉自己。上游就是这么写的，
 * 主要用途是给整合包作者 / 调试用。</p>
 *
 * <h2>与上游的差异</h2>
 * <table border="1">
 *     <tr><th>上游</th><th>本移植版</th><th>原因</th></tr>
 *     <tr>
 *         <td>{@code initializeClient} + {@code IItemRenderProperties}</td>
 *         <td>不写；渲染器在 {@code TouhouLittleMaidFutoClient} 里用
 *             {@code BuiltinItemRendererRegistry} 注册</td>
 *         <td>Fabric 没有 Forge 那套物品渲染属性（坐垫 / 手办 / 野餐篮同一套做法）</td>
 *     </tr>
 *     <tr>
 *         <td>{@code getRecipeManager().byType(...).get(id)}</td>
 *         <td>遍历 {@code getAllRecipesFor(...)} 比对 id</td>
 *         <td>1.18.2 没有 {@code byType} 的按 id 取值形式；本仓库读全量配方的写法见
 *             {@code compat/patchouli/AltarRecipeComponent}</td>
 *     </tr>
 * </table>
 */
public class ItemEntityPlaceholder extends Item {
    private static final String RECIPE_ID_TAG = "RecipeId";

    public ItemEntityPlaceholder() {
        super(new FabricItemSettings().stacksTo(1).tab(InitItems.MAIN_TAB));
    }

    /**
     * 把祭坛配方 id 写进物品 NBT，进度 JSON 的 {@code display.icon} 就是这么写的。
     */
    public static ItemStack setRecipeId(ItemStack stack, ResourceLocation id) {
        stack.getOrCreateTag().putString(RECIPE_ID_TAG, id.toString());
        return stack;
    }

    /**
     * 读回配方 id。<b>解析失败返回 {@code null} 而不是抛异常</b> ——
     * 这个值会被 {@code getName}、渲染器与右键逻辑同时读到，
     * 一件被 {@code /give} 塞了乱码 NBT 的物品不应该让客户端崩在渲染线程上。
     */
    @Nullable
    public static ResourceLocation getRecipeId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(RECIPE_ID_TAG, Tag.TAG_STRING)) {
            return null;
        }
        String raw = tag.getString(RECIPE_ID_TAG);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return new ResourceLocation(raw);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        if (context.getClickedFace() != Direction.UP || !(world instanceof ServerLevel serverLevel)) {
            return super.useOn(context);
        }
        ResourceLocation id = getRecipeId(context.getItemInHand());
        if (id == null || !(world instanceof ServerLevel)) {
            return super.useOn(context);
        }
        // 1.18.2 没有「按 id 取配方」的公开方法，遍历一遍（配方总数 40 上下，右键频率极低）
        List<AltarRecipe> recipes = serverLevel.getRecipeManager().getAllRecipesFor(InitRecipes.ALTAR_CRAFTING);
        for (AltarRecipe recipe : recipes) {
            if (recipe.getId().equals(id)) {
                BlockPos pos = context.getClickedPos().above();
                recipe.spawnOutputEntity(serverLevel, pos, null);
                context.getItemInHand().shrink(1);
                break;
            }
        }
        return super.useOn(context);
    }

    /**
     * 创造模式物品栏里铺开「所有非物品类祭坛配方」各一份，与上游一致。
     *
     * <p>只在主物品栏填，而且只在客户端填 —— 另外两栏（坐垫 / 手办）与专用服务器上
     * 都不该出现这个物品。</p>
     */
    @Override
    public void fillItemCategory(CreativeModeTab group, NonNullList<ItemStack> items) {
        // 注意【不要】在这里标 @Environment(EnvType.CLIENT)：fabric-loader 会在专用服务器上
        // 把带这个注解的方法整段剥掉，而它是 Item#fillItemCategory 的覆写 ——
        // 本仓库其它物品（坐垫 / 手办 / 野餐篮）一律用 FabricLoader 的环境判定，这里照做。
        if (group != InitItems.MAIN_TAB || !this.allowdedIn(group)) {
            return;
        }
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            return;
        }
        List<AltarRecipe> recipes = Minecraft.getInstance().level.getRecipeManager()
                .getAllRecipesFor(InitRecipes.ALTAR_CRAFTING);
        for (AltarRecipe recipe : recipes) {
            if (!recipe.isItemCraft()) {
                items.add(setRecipeId(new ItemStack(this), recipe.getId()));
            }
        }
    }

    /**
     * 名字跟着配方走：{@code jei.<命名空间>.altar_craft.<配方文件名>.result}。
     *
     * <p>这条键本来就在语言文件里（JEI 页面的「结果：xxx」用的就是它），
     * 所以这里不必新增任何翻译。</p>
     */
    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation recipeId = getRecipeId(stack);
        if (recipeId != null) {
            String namespace = recipeId.getNamespace().toLowerCase(Locale.US);
            String path = recipeId.getPath();
            int slash = path.lastIndexOf('/');
            String fileName = (slash < 0 ? path : path.substring(slash + 1)).toLowerCase(Locale.US);
            return new TranslatableComponent(
                    String.format("jei.%s.altar_craft.%s.result", namespace, fileName));
        }
        return new TranslatableComponent("item.touhou_little_maid.entity_placeholder");
    }
}

