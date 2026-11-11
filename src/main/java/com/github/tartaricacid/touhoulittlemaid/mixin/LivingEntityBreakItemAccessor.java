package com.github.tartaricacid.touhoulittlemaid.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 把原版私有的 {@code LivingEntity#breakItem} 暴露出来。
 *
 * <p><b>为什么需要它：</b>装备耐久归零时，原版会调 {@code breakItem} 播放碎裂音并甩出碎屑粒子。
 * 上游 TLM 在女仆饰品碎掉时直接调它（{@code ((LivingEntity) e).breakItem(message.item)}）——
 * Forge 用 AccessTransformer 把这个方法改成了 public，所以上游能这么写；
 * Fabric 不修改原版可见性，1.18.2 的 Fabric API 也没有对应入口。</p>
 *
 * <p>这里用 {@code @Invoker} 访问器绕开访问修饰符：<b>不改变任何行为</b>，
 * 只是让「服务端通知客户端去播碎裂表现」这条链路能落地。
 * 该方法本身是 {@code @Environment(CLIENT)} 的，因此只能在客户端接收包里调用。</p>
 */
@Mixin(LivingEntity.class)
public interface LivingEntityBreakItemAccessor {
    @Invoker("breakItem")
    static void invokeBreakItem(LivingEntity entity, ItemStack stack) {
        throw new AssertionError("Mixin invoker was not applied: LivingEntity#breakItem");
    }
}

