package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
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
 * 实体 ID 复制器，移植自上游 {@code item/ItemEntityIdCopy}。
 *
 * <p>拿在手上右键任意实体，它的注册名（如 {@code minecraft:zombie}）会被复制到剪贴板，
 * 并在聊天栏回一行提示。用途是填「女仆攻击目标列表」这类需要手打实体 id 的地方 ——
 * 上游的 tooltip 原文就是「方便填写攻击列表」。</p>
 *
 * <p>贴图 / 模型 / 语言键在 baseline 里<b>本来就有</b>（
 * {@code models/item/entity_id_copy.json}、{@code textures/item/entity_id_copy.png}、
 * 三个语言键），缺的只是物品注册和右键行为 —— 这也是之前没有对应配方的直接原因。</p>
 *
 * <p>行为侧在 {@code event/CopyEntityIdEvent} 里（Fabric 用 {@code UseEntityCallback}）。</p>
 */
public class ItemEntityIdCopy extends Item {
    public ItemEntityIdCopy() {
        // 上游是 stacksTo(1) + 进主创造页签
        super(new Properties().stacksTo(1).tab(InitItems.MAIN_TAB));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(new TranslatableComponent("tooltips.touhou_little_maid.entity_id_copy.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}

