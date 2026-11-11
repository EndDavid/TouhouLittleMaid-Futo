package com.github.tartaricacid.touhoulittlemaid.api.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 女仆即将播放语音时触发（上游同名事件）。
 *
 * <p>取消它可以吞掉这一声 —— 静音饰品（{@code mute_bauble}）就是靠它实现的。
 * 事件在服务端触发。</p>
 */
@Cancelable
public class MaidPlaySoundEvent extends Event {
    private final EntityMaid maid;

    public MaidPlaySoundEvent(EntityMaid maid) {
        this.maid = maid;
    }

    public EntityMaid getMaid() {
        return maid;
    }
}

