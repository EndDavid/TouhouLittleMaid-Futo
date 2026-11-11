package com.github.tartaricacid.touhoulittlemaid.item.bauble;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidPlaySoundEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/**
 * 静音饰品，移植自上游 {@code item/bauble/MuteBauble}。
 *
 * <p>它自己不做任何事，只是「取消女仆的语音事件」。三处语音（环境音 / 受伤 / 死亡）
 * 都先问一次 {@code MaidPlaySoundEvent}，取消即静音。</p>
 *
 * <p>顺带把 {@link #getChatBubbleId()} 返回 {@code "mute"}：上游用它让女仆头顶
 * 显示一个「被静音」的气泡。本移植版的聊天气泡系统尚未移植，因此该 id 目前无消费方。</p>
 */
public class MuteBauble implements IMaidBauble {
    public MuteBauble() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingDamage(MaidPlaySoundEvent event) {
        EntityMaid maid = event.getMaid();
        int slot = ItemsUtil.getBaubleSlotInMaid(maid, this);
        if (slot >= 0) {
            event.setCanceled(true);
        }
    }

    @Override
    public String getChatBubbleId() {
        return "mute";
    }
}

