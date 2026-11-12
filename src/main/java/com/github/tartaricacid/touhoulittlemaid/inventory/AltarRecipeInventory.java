package com.github.tartaricacid.touhoulittlemaid.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 祭坛合成的「材料视图」，逐字移植自上游 {@code inventory/AltarRecipeInventory}。
 *
 * <p>它不是真正的容器，只是一次合成判定用的临时快照：把祭坛顶部 4 根原木上放的贡品
 * 按 {@code canPlaceItemPosList} 的顺序抄进这 6 个槽，然后丢给 {@code RecipeManager} 匹配。</p>
 *
 * <p>槽位数固定 6，与上游 {@code RECIPES_SIZE} 一致 —— 祭坛四角 + 两条中线，
 * 由 {@code altar_*.nbt} 结构模板里 {@code y == 2} 的原木位置决定。</p>
 */
public class AltarRecipeInventory implements Container {
    public static final int RECIPES_SIZE = 6;

    public final NonNullList<ItemStack> items = NonNullList.withSize(RECIPES_SIZE, ItemStack.EMPTY);

    public AltarRecipeInventory() {
    }

    /**
     * 找出第一个满足该原料的贡品（祭坛生成产物时用它决定「抄哪个材料的 NBT」，见
     * {@code AltarRecipe#copyIngredientTag}）。
     */
    public ItemStack getMatchIngredient(Ingredient ingredient) {
        return items.stream().filter(ingredient).findFirst().orElse(ItemStack.EMPTY);
    }

    @Override
    public int getContainerSize() {
        return RECIPES_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return this.getContainerSize() <= index ? ItemStack.EMPTY : this.items.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        return ContainerHelper.removeItem(this.items, index, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return ContainerHelper.takeItem(this.items, index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        this.items.set(index, stack);
    }

    @Override
    public void setChanged() {
        // 临时视图，没有需要通知的监听者
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    public NonNullList<ItemStack> getItems() {
        return items;
    }
}

