package com.github.tartaricacid.touhoulittlemaid.api.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 女仆即将死亡时触发（上游同名事件）。
 *
 * <p>触发点在 {@code EntityMaid#die} 里、{@code super.die(...)} 之前，
 * 所以取消它就是「这次死亡不发生」。不死图腾饰品与极意（复活）饰品都挂在这里，
 * 且都用 {@code EventPriority.HIGH} —— 必须先于其他监听者判断，
 * 否则复活之后仍会有别的监听者按「已经死了」处理。</p>
 *
 * <p><b>与上游的差异：</b>上游继承 {@code LivingEvent}，本移植版改为直接继承 {@link Event}。</p>
 */
@Cancelable
public class MaidDeathEvent extends Event {
    private final EntityMaid maid;
    private final DamageSource source;

    public MaidDeathEvent(EntityMaid maid, DamageSource source) {
        this.maid = maid;
        this.source = source;
    }

    public EntityMaid getMaid() {
        return maid;
    }

    public DamageSource getSource() {
        return source;
    }
}

