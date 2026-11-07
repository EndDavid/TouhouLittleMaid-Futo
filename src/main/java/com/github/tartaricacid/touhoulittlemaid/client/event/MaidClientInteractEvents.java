package com.github.tartaricacid.touhoulittlemaid.client.event;

import com.github.tartaricacid.touhoulittlemaid.client.render.MaidAreaRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.screen.NameTagScreen;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 「指南针 / 命名牌右击自己的女仆」的<b>客户端</b>那一半。
 *
 * <p><b>为什么需要它：</b>这两个功能的表现都是纯客户端的 ——
 * 指南针要在世界里画工作区域，命名牌要打开输入框。但 {@code mobInteract}
 * 写在 common 侧的 {@code EntityMaid} 里，如果在那里直接
 * {@code Minecraft.getInstance().setScreen(...)}，专用服务端也会去加载
 * 客户端类而崩服。</p>
 *
 * <p>Fabric 里没有 Forge 的 {@code DistExecutor}，标准做法是把客户端逻辑放进
 * 客户端初始化器注册的事件里。这里选 {@code UseEntityCallback}：它在
 * {@code Player#interactOn} 的开头触发，<b>早于</b> {@code mobInteract}，
 * 两端都会跑 —— 我们只处理 {@code world.isClientSide()} 那一半。</p>
 *
 * <p>服务端侧对应的处理在 {@code EntityMaid#mobInteract} 里：
 * 它会把这两种物品的交互「吃掉」（返回 SUCCESS），从而不打开女仆界面。
 * 分工是「客户端做表现、服务端只负责拦住默认行为」。</p>
 */
@Environment(EnvType.CLIENT)
public final class MaidClientInteractEvents {
    private MaidClientInteractEvents() {
    }

    public static void init() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            // 这个事件两端都会触发，服务端那一半交给 EntityMaid#mobInteract
            if (!world.isClientSide() || hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (!(entity instanceof EntityMaid maid) || !maid.isOwnedByPlayer(player)) {
                return InteractionResult.PASS;
            }
            ItemStack stack = player.getItemInHand(hand);

            if (stack.is(Items.COMPASS)) {
                MaidAreaRenderer.show(maid);
                // 返回 PASS，让本地的 mobInteract 也照常跑一遍：
                // 它会在服务端把「开界面」那条路堵死，两端行为保持一致
                return InteractionResult.PASS;
            }

            if (stack.is(Items.NAME_TAG) && !stack.hasCustomHoverName()) {
                Minecraft.getInstance().setScreen(new NameTagScreen(maid));
                // 返回 SUCCESS：这次点击已经被界面接管，不要再触发手上的动作
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.PASS;
        });
    }
}

