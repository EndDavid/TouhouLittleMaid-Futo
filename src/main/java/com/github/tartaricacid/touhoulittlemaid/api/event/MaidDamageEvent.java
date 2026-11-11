package com.github.tartaricacid.touhoulittlemaid.api.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 女仆的伤害已经过护甲 / 抗性 / 吸收结算，即将扣血时触发（上游同名事件）。
 *
 * <p>触发点在 {@code EntityMaid#actuallyHurt} 里、{@code setHealth} 之前。
 * 因此 {@link #getAmount()} 是<b>真正会扣掉的血量</b>，取消它等于「这一下被完全吸收」——
 * 保护类饰品（摔落 / 爆炸 / 火焰 / 溺水 / 魔法 / 投射物）全部挂在这里。</p>
 *
 * <p><b>与上游的差异：</b>上游继承 {@code LivingEvent}，本移植版的兼容层没有该基类，
 * 因此直接继承 {@link Event}。语义不变。</p>
 */
@Cancelable
public class MaidDamageEvent extends Event {
    private final EntityMaid maid;
    private final DamageSource source;
    private float amount;

    public MaidDamageEvent(EntityMaid maid, DamageSource source, float amount) {
        this.maid = maid;
        this.source = source;
        this.amount = amount;
    }

    public EntityMaid getMaid() {
        return maid;
    }

    public DamageSource getSource() {
        return source;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = amount;
    }
}

