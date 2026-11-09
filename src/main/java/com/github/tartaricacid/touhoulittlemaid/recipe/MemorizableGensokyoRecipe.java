package com.github.tartaricacid.touhoulittlemaid.recipe;

import com.github.tartaricacid.touhoulittlemaid.compat.patchouli.PatchouliCompat;
import com.github.tartaricacid.touhoulittlemaid.init.InitRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 合成《记忆中的幻想乡》手册的配方，对应上游
 * {@code data/touhou_little_maid/recipes/memorizable_gensokyo.json}。
 *
 * <p><b>为什么不是一个普通的 {@code crafting_shapeless}：</b>上游 Forge 版的结果类型是
 * {@code minecraft:item_nbt}，靠它给结果物品挂上 {@code patchouli:book} 标签。
 * 但原版 1.18.2 的 {@code ShapedRecipe#itemStackFromJson} 明确拒绝 NBT
 * （反编译可见 {@code Disallowed data tag found} 分支），
 * 所以「三样材料 → 带标签的手册」在 Fabric 1.18.2 上<b>无法用数据包表达</b>，
 * 结果只能由代码构造。这里沿用原版 {@code crafting_special_bookcloning}
 * （{@link CustomRecipe} + {@code SimpleRecipeSerializer}）的写法：
 * 数据包里只留 {@code {"type": "touhou_little_maid:memorizable_gensokyo"}}，
 * 材料与结果都在 {@link #matches} / {@link #getResultItem} 里给出。
 * 这样做同时避开了「自定义结果类型」所需的网络同步代码 —— 特殊配方本来就不带参数。</p>
 *
 * <p>材料与上游一致：白色染料 + 红色染料 + 书。上游用的是
 * {@code forge:dyes/white} / {@code forge:dyes/red} 标签，Fabric 1.18.2 上没有对应的
 * 通用标签，而原版染料标签里也只有这一个物品，因此直接写死 {@code minecraft:white_dye}
 * 与 {@code minecraft:red_dye}，语义完全相同。</p>
 */
public class MemorizableGensokyoRecipe extends CustomRecipe {
    /**
     * 帕秋莉用来标记「这本书是哪一本」的 NBT 键。
     */
    public static final String BOOK_TAG = "patchouli:book";

    private static final String PATCHOULI_GUIDE_BOOK = "guide_book";

    private final NonNullList<Ingredient> ingredients = NonNullList.create();

    public MemorizableGensokyoRecipe(ResourceLocation id) {
        super(id);
        this.ingredients.add(Ingredient.of(Items.WHITE_DYE));
        this.ingredients.add(Ingredient.of(Items.RED_DYE));
        this.ingredients.add(Ingredient.of(Items.BOOK));
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        // 没装帕秋莉时连材料都不该被吃进去：数据包那侧也用 fabric:load_conditions 挡了一道，
        // 这里是「万一条件没生效」的第二道保险
        if (!PatchouliCompat.isLoaded()) {
            return false;
        }
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) {
                count++;
            }
        }
        if (count != this.ingredients.size()) {
            return false;
        }
        // 无序匹配：每个非空格子找一个还没被占用的材料
        boolean[] used = new boolean[this.ingredients.size()];
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            int found = -1;
            for (int j = 0; j < this.ingredients.size(); j++) {
                if (!used[j] && this.ingredients.get(j).test(stack)) {
                    found = j;
                    break;
                }
            }
            if (found < 0) {
                return false;
            }
            used[found] = true;
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingContainer container) {
        return this.getResultItem().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= this.ingredients.size();
    }

    /**
     * 结果就是一本挂好 {@code patchouli:book} 标签的 {@code patchouli:guide_book}。
     *
     * <p>物品是运行时按 id 查的，本类因此不引用任何 {@code vazkii.patchouli} 类型，
     * 未安装帕秋莉时加载本类也不会 {@code NoClassDefFoundError}。</p>
     */
    @Override
    public ItemStack getResultItem() {
        Item guideBook = Registry.ITEM.get(new ResourceLocation(PatchouliCompat.MOD_ID, PATCHOULI_GUIDE_BOOK));
        if (guideBook == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(guideBook);
        CompoundTag tag = new CompoundTag();
        tag.putString(BOOK_TAG, PatchouliCompat.BOOK_ID.toString());
        stack.setTag(tag);
        return stack;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.ingredients;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return InitRecipes.MEMORIZABLE_GENSOKYO;
    }
}

