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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 「坐垫碰撞箱显示器」，移植自上游 {@code item/ItemChairShow}。
 *
 * <p>拿在手上蹲下时，{@code EntityChairRenderer} 会把所有坐垫画成红色线框，
 * 用来精确对齐多方块家具。它自己不做任何事。</p>
 */
public class ItemChairShow extends Item {
    public ItemChairShow() {
        super(new FabricItemSettings().tab(InitItems.MAIN_TAB).stacksTo(1));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(new TranslatableComponent("tooltips.touhou_little_maid.chair_show.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
