package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 「女仆身上某件装备碎了」的 S2C 包，移植自上游 {@code network/message/ItemBreakMessage}。
 *
 * <p><b>为什么需要它：</b>耐久归零是<b>服务端</b>算出来的（{@code ItemStack#hurtAndBreak}
 * 在耐久耗尽时直接清空物品），而 {@code LivingEntity#breakItem} 是<b>纯客户端</b>方法
 * —— 它会播放 {@code SoundEvents.ITEM_BREAK} 并甩出一把碎屑粒子。
 * 服务端调它什么也不会发生，所以必须由服务端通知客户端「去播一下」。
 * 少了这个包的表现是：饰品碎了，但玩家听不到任何声音、也看不到碎屑，
 * 只会突然发现「饰品没了」。</p>
 *
 * <p>包里带上 {@link ItemStack} 本体（而不是只带实体 id）：{@code breakItem} 需要它
 * 才能生成正确的碎屑贴图 —— 上游也是这样做的。</p>
 */
public final class ItemBreakPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "item_break");

    private static final double NEARBY_RADIUS = 16.0D;

    private ItemBreakPacket() {
    }

    /**
     * 服务端调用：通知附近玩家「这只女仆手上的某件物品碎了」。
     */
    public static void sendNearby(EntityMaid maid, ItemStack stack) {
        if (!(maid.level instanceof ServerLevel serverLevel) || stack.isEmpty()) {
            return;
        }
        double radiusSqr = NEARBY_RADIUS * NEARBY_RADIUS;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(maid) > radiusSqr) {
                continue;
            }
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeInt(maid.getId());
            buf.writeItem(stack);
            ServerPlayNetworking.send(player, CHANNEL, buf);
        }
    }

    /**
     * 客户端接收后调用 {@code LivingEntity#breakItem}。
     */
    public static void handle(EntityMaid maid, ItemStack stack) {
        if (!maid.isAlive()) {
            return;
        }
        // LivingEntity#breakItem is private in 1.18.2 (Forge opens it through an AT),
        // so the call goes through the @Invoker accessor.
        com.github.tartaricacid.touhoulittlemaid.mixin.LivingEntityBreakItemAccessor
                .invokeBreakItem(maid, stack);
    }
}

