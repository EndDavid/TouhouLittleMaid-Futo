package com.github.tartaricacid.touhoulittlemaid.client.event;

import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;

/**
 * 实体 ID 复制器的右键行为，移植自上游 {@code event/CopyEntityIdEvent}。
 *
 * <p>上游是 Forge 的 {@code PlayerInteractEvent.EntityInteract}，并且<b>只在客户端</b>
 * 做复制（{@code FMLEnvironment.dist == Dist.CLIENT}）—— 因为剪贴板与聊天提示都是本地的东西。
 * Fabric 上没有这个事件，走 {@link UseEntityCallback}：它在 {@code Player#interactOn}
 * 开头触发、两端都会跑，我们只处理客户端那一半（与 {@code MaidClientInteractEvents} 同一套路）。</p>
 *
 * <p><b>返回 SUCCESS 的意义</b>：让这次点击就此结束，不要再把物品当普通物品使用，
 * 也不要触发实体的 {@code mobInteract}（否则对着女仆右键会顺手把女仆界面打开）。
 * 服务端也会走到这个回调、返回同样的 SUCCESS，因此两端行为一致，不会出现「客户端以为点完了、
 * 服务端却打开界面」的错位。</p>
 */
@Environment(EnvType.CLIENT)
public final class CopyEntityIdEvent {
    private CopyEntityIdEvent() {
    }

    public static void init() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!player.getItemInHand(hand).is(InitItems.ENTITY_ID_COPY)) {
                return InteractionResult.PASS;
            }
            if (world.isClientSide()) {
                // 上游用 ForgeRegistries.ENTITIES.getKey；Fabric 上就是 Registry.ENTITY_TYPE.getKey
                ResourceLocation key = Registry.ENTITY_TYPE.getKey(entity.getType());
                if (key != null) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(key.toString());
                    player.sendMessage(new TranslatableComponent(
                            "message.touhou_little_maid.entity_id_copy.copy", key.toString()), Util.NIL_UUID);
                }
            }
            // 两端都吃掉这次点击：物品不参与任何其它交互
            return InteractionResult.SUCCESS;
        });
    }
}

