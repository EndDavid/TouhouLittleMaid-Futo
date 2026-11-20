package com.github.tartaricacid.touhoulittlemaid.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 把原版 {@code LivingEntity#jumping} 暴露出来。
 *
 * <p><b>为什么需要它：</b>扫帚要用「跳跃键」控制上升，而跳跃状态在 1.18.2 里是
 * {@code protected boolean jumping} —— 既没有公开的读取方法，也不在
 * {@code Player} 的任何接口上。上游 1.20.1 里这个字段是可读的（它甚至自己也有一个
 * {@code LivingEntityAccessor}），所以上游 {@code IBroomControl.keyJump} 能直接问
 * 「玩家现在按着跳吗」。</p>
 *
 * <p><b>它是双端可用的：</b>客户端 {@code ServerboundPlayerInputPacket} 带着
 * {@code isJumping}，服务端 {@code ServerPlayer#setPlayerInput} 会把
 * <b>正在骑乘时</b>（{@code isPassenger()}）收到的值写进同一个字段。
 * 也就是说「骑着扫帚」这个前提下，两边的 {@code jumping} 都是真实按键状态 ——
 * 这正是扫帚能把控制逻辑搬到服务端的前提。</p>
 *
 * <p>只是绕开访问修饰符，不改变任何行为。</p>
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("jumping")
    boolean tlmIsJumping();
}

