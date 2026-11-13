package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 好感度工具，逐字移植自上游 {@code item/ItemFavorabilityTool}。
 *
 * <p>一个物品类承载三个变体，靠 {@code type} 字符串区分：
 * {@code add} / {@code reduce} / {@code full}。物品本身没有任何行为 ——
 * 真正的效果在 {@code EntityMaid#mobInteract} 里（上游是
 * {@code event/maid/UseFavorabilityToolEvent}，一个 {@code InteractMaidEvent} 订阅者）。</p>
 *
 * <p>三个工具都是创造模式专属：没有配方、不能合成，只能从创造模式物品栏拿。
 * 上游 {@code stacksTo(1)} 但没有 {@code durability}，所以也不可损耗。</p>
 */
public class ItemFavorabilityTool extends Item {
    private final String type;

    public ItemFavorabilityTool(String type) {
        super(new FabricItemSettings().stacksTo(1).tab(InitItems.MAIN_TAB));
        this.type = type;
    }

    /**
     * 附魔光效常驻：这三个物品不是附魔品，但闪烁效果让它们在物品栏里一眼可辨，
     * 上游 {@code isFoil} 直接返回 true。
     */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /**
     * 物品说明按变体区分，键名 {@code tooltips.touhou_little_maid.favorability_tool.<type>}。
     *
     * <p>上游用的是 {@code new TranslatableComponent(...).withStyle(ChatFormatting.GRAY)}。
     * 1.18.2 的 {@code Component} 就是 {@code MutableComponent} 的父接口，
     * 所以 {@code withStyle} 的链式调用照抄即可。</p>
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltips, TooltipFlag flag) {
        tooltips.add(new TranslatableComponent(
                "tooltips.touhou_little_maid.favorability_tool." + this.type)
                .withStyle(ChatFormatting.GRAY));
    }
}
