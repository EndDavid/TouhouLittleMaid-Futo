package com.github.tartaricacid.touhoulittlemaid.client.event;

import com.github.tartaricacid.touhoulittlemaid.entity.backpack.BackpackManager;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/**
 * 物品说明（tooltip）补充，移植自上游 {@code client/event/AddInformationEvent}。
 *
 * <p>给两类物品各追加三行：</p>
 * <pre>
 * （空行）
 * §a[女仆饰品]                    ← tooltips.touhou_little_maid.bauble.desc
 * 需要放置在女仆的饰品栏中（灰）     ← tooltips.touhou_little_maid.bauble.usage
 * </pre>
 * <p>背包同理，用的是 {@code tooltips.touhou_little_maid.backpack.*}。</p>
 *
 * <h2>与上游的差异：事件来源</h2>
 *
 * <p>上游挂 Forge 的 {@code ItemTooltipEvent}（{@code EventPriority.LOWEST}，
 * 也就是「等别的模组都加完了我再加」，保证这几行永远在最下面）。
 * Fabric 没有这个事件，等价物是 {@link ItemTooltipCallback} ——
 * 它在 {@code ItemStack#getToolTipLines} 收集完原版行之后触发，
 * 位置同样在末尾，语义一致。</p>
 *
 * <h2>为什么判定用 {@code BaubleManager} 而不是「物品实现了某接口」</h2>
 *
 * <p>饰品是「物品 + 行为」的绑定关系，物品本身是个普通 {@code Item}
 * （见 {@code BaubleManager} 的类注释）。所以「是不是饰品」只能查这张表 ——
 * 这也顺带覆盖了 {@code minecraft:totem_of_undying}：它没经过本模组注册，
 * 但上游同样把它绑成了饰品，说明里也就该出现这几行。</p>
 *
 * <p>只在客户端注册：{@code ItemTooltipCallback} 属于客户端的 item API，
 * 专用服务端不需要、也不该加载这个类。</p>
 */
@Environment(EnvType.CLIENT)
public final class AddInformationEvent {
    private AddInformationEvent() {
    }

    public static void init() {
        ItemTooltipCallback.EVENT.register((stack, tooltipFlag, lines) -> {
            if (stack.isEmpty()) {
                return;
            }
            if (BaubleManager.getBauble(stack) != null) {
                lines.add(new TextComponent(" "));
                lines.add(new TranslatableComponent("tooltips.touhou_little_maid.bauble.desc"));
                lines.add(new TranslatableComponent("tooltips.touhou_little_maid.bauble.usage")
                        .withStyle(ChatFormatting.GRAY));
            }
            if (BackpackManager.findBackpack(stack).isPresent()) {
                lines.add(new TextComponent(" "));
                lines.add(new TranslatableComponent("tooltips.touhou_little_maid.backpack.desc"));
                lines.add(new TranslatableComponent("tooltips.touhou_little_maid.backpack.usage")
                        .withStyle(ChatFormatting.GRAY));
            }
        });
    }
}

