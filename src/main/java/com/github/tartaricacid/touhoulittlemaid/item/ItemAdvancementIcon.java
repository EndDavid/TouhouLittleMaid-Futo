package com.github.tartaricacid.touhoulittlemaid.item;

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
 * 进度图标物品，移植自上游 {@code item/ItemAdvancementIcon}。
 *
 * <p>它<b>不是给玩家用的</b>：八件进度（{@code all_netherite_equipment} / {@code kill_100} /
 * {@code kill_wither} / {@code kill_dragon} / {@code kill_slime_300} / {@code maid_100_healthy} /
 * {@code change_maid_model} / {@code change_chair_model}）的图标不是原版物品能表达的，
 * 上游就各注册了一件「只有贴图」的物品，只用来挂 {@code display.icon}。</p>
 *
 * <p>所以三件事必须与上游一致：</p>
 * <ul>
 *     <li><b>不进任何创造模式物品栏</b> —— 不写 {@code tab(...)}，与
 *         {@code GOMOKU_BOARD_STATE} 那三件残局道具同一个做法；</li>
 *     <li>{@code stacksTo(1)}，免得被当成可堆叠的普通材料；</li>
 *     <li>统一覆写 {@code getDescriptionId()}，八件共用一个名字「进度图标」，
 *         这样 {@code /give} 拿到手也不会看起来像八种不同的东西。</li>
 * </ul>
 */
public class ItemAdvancementIcon extends Item {
    /**
     * 八件共用的物品名（上游同样覆写了 {@code getDescriptionId()}）。
     */
    private static final String DESCRIPTION_ID = "item.touhou_little_maid.advancement_icon";
    private static final String TOOLTIP_KEY = "tooltips.touhou_little_maid.advancement_icon.desc";

    public ItemAdvancementIcon() {
        super(new FabricItemSettings().stacksTo(1));
    }

    @Override
    public String getDescriptionId() {
        return DESCRIPTION_ID;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(new TranslatableComponent(TOOLTIP_KEY).withStyle(ChatFormatting.GRAY));
    }
}

