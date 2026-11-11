package com.github.tartaricacid.touhoulittlemaid.api.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 女仆即将受伤时触发（上游同名事件）。
 *
 * <p>触发点在 {@code EntityMaid#hurt} 的最前面 —— 此时护甲、抗性、吸收都还没结算，
 * 所以这里的 {@code amount} 是<b>原始</b>伤害值。取消它等于「这一下完全没打中」。</p>
 *
 * <p><b>与上游的差异：</b>上游继承 {@code net.minecraftforge.event.entity.living.LivingEvent}
 * （Forge 的实体事件基类）。本移植版的 Forge 兼容层没有实体事件体系，
 * 因此改为直接继承 {@link Event}，并自己带 {@link #getMaid()} —— 语义不变。</p>
 */
@Cancelable
public class MaidAttackEvent extends Event {
    private final EntityMaid maid;
    private final DamageSource source;
    private final float amount;

    public MaidAttackEvent(EntityMaid maid, DamageSource source, float amount) {
        this.source = source;
        this.amount = amount;
        this.maid = maid;
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
}

