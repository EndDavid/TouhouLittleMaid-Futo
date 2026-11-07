package com.github.tartaricacid.touhoulittlemaid.mixin;

import com.github.tartaricacid.touhoulittlemaid.capability.IPower;
import com.github.tartaricacid.touhoulittlemaid.capability.PowerCapability;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 给 {@link Player} 挂上灵力（Power）数据 —— 上游用 Forge capability 做的事。
 *
 * <p><b>为什么用 Mixin 而不是自己维护一张 UUID → 数值的表：</b>
 * 玩家对象在「切维度 / 重生 / 断线重连」时会被替换或重建，外部表要么漏掉清理、
 * 要么在新对象上读到旧值；而且客户端（{@code RemotePlayer}）也需要同一份数据来画 HUD。
 * 把字段直接长在 {@code Player} 上，生命周期天然跟随实体，客户端与服务端各有一份，
 * 由同步包负责对齐 —— 这正是上游 capability 的语义。</p>
 *
 * <p><b>字段为什么懒初始化：</b>{@code @Unique} 字段的初始化器会被 Mixin 合并进
 * 目标类的所有构造器；{@code Player} 在 1.18.2 有多个构造器且部分会被
 * {@code ServerPlayer} 链式调用，懒初始化可以完全绕开「构造器合并」这一层的风险，
 * 代价只是一次 {@code null} 判断。</p>
 */
@Mixin(Player.class)
public abstract class PlayerPowerMixin implements IPower {
    @Unique
    private PowerCapability tlm$power;

    @Override
    public PowerCapability getPower() {
        if (this.tlm$power == null) {
            this.tlm$power = new PowerCapability();
        }
        return this.tlm$power;
    }
}

