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
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 替身地藏，移植自上游 {@code item/ItemSubstituteJizo}。
 *
 * <p>对<b>自己的</b>女仆右键，让她永久无敌（{@code setEntityInvulnerable(true)}），
 * 物品消耗一个。这是「不想她死」的最后保险 —— 代价是再也拿不回来。</p>
 *
 * <h2>为什么逻辑不在这个类里</h2>
 * <p>上游把处理器写成 {@code @SubscribeEvent static void onEntityInteract(InteractMaidEvent)}
 * —— 事件监听器，挂在 Forge 事件总线上。本移植版<b>没有</b> {@code InteractMaidEvent}
 * （Fabric 没有事件总线，女仆的右键处理全部显式串在
 * {@code EntityMaid#mobInteract} 里），所以行为挪到了
 * {@code EntityMaid#applySubstituteJizo}，这里只留物品定义与提示。
 * 物品判断用的仍然是 {@code stack.getItem() == InitItems.SUBSTITUTE_JIZO}，判据不变。</p>
 */
public class ItemSubstituteJizo extends Item {
    public ItemSubstituteJizo() {
        super(new FabricItemSettings().stacksTo(1).tab(InitItems.MAIN_TAB));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level worldIn,
                                List<Component> tooltip, TooltipFlag flagIn) {
        tooltip.add(new TranslatableComponent("tooltips.touhou_little_maid.substitute_jizo.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}

