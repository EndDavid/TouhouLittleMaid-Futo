package com.github.tartaricacid.touhoulittlemaid.client.event;

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
 * 「命名牌右击自己的女仆」的<b>客户端</b>那一半，以及原版指南针残留分支的说明。
 *
 * <p><b>为什么需要它：</b>命名牌要打开输入框，而 {@code mobInteract} 写在 common 侧的
 * {@code EntityMaid} 里，如果在那里直接 {@code Minecraft.getInstance().setScreen(...)}，
 * 专用服务端也会去加载客户端类而崩服。</p>
 *
 * <p>Fabric 里没有 Forge 的 {@code DistExecutor}，标准做法是把客户端逻辑放进
 * 客户端初始化器注册的事件里。这里选 {@code UseEntityCallback}：它在
 * {@code Player#interactOn} 的开头触发，<b>早于</b> {@code mobInteract}，
 * 两端都会跑 —— 我们只处理 {@code world.isClientSide()} 那一半。</p>
 *
 * <p><b>原版指南针已经不在这里处理了。</b>早期版本在这条回调里直接调
 * {@code MaidAreaRenderer.show(maid)}，那是在「客户端自己猜女仆的作息坐标」；
 * 现在三个坐标由服务端通过 {@code MaidAreaPacket} 发过来（上游
 * {@code MaidAreaClickEvent} 也是服务端行为），客户端只负责画。</p>
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

            // 原版指南针的区域显示已经改成服务端发包（上游 MaidAreaClickEvent 也是服务端做的），
            // 这里不再需要客户端分支：客户端只要收到 MaidAreaPacket 就会画，
            // 而「不要打开女仆界面」这件事由服务端的 mobInteract 返回 SUCCESS 来保证。

            if (stack.is(Items.NAME_TAG) && !stack.hasCustomHoverName()) {
                Minecraft.getInstance().setScreen(new NameTagScreen(maid));
                // 返回 SUCCESS：这次点击已经被界面接管，不要再触发手上的动作
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.PASS;
        });
    }
}
