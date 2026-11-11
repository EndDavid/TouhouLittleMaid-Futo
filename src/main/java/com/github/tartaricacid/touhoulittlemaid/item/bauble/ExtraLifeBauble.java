package com.github.tartaricacid.touhoulittlemaid.item.bauble;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.network.MaidParticlePacket;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 极意（复活）饰品，移植自上游 {@code item/bauble/ExtraLifeBauble}。
 *
 * <p>挂 {@link MaidDeathEvent} 而不是 {@code MaidDamageEvent}：它要的效果是
 * 「这次死亡不发生，并且回满血」。挂在伤害事件上做不到这一点 ——
 * 伤害结算完之后女仆照样可能死。</p>
 *
 * <p><b>{@code source.isBypassInvul()} 这一道不能省：</b>
 * {@code /kill}、掉进虚空等伤害在语义上就是「不可复活」的，
 * 上游据此跳过一次，防止把女仆从虚空里反复拉回来。</p>
 *
 * <p>扣耐久用的是 {@code hurtAndBreak(...)} 而不是 {@code shrink(1)} ——
 * 30 点耐久的饰品要用 30 次才会碎，扣光时 {@code hurtAndBreak} 会把物品清空，
 * 并回调 {@code sendItemBreakMessage} 让客户端播碎屑音效。</p>
 */
public class ExtraLifeBauble implements IMaidBauble {
    public ExtraLifeBauble() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingDamage(MaidDeathEvent event) {
        EntityMaid maid = event.getMaid();
        DamageSource source = event.getSource();
        if (!source.isBypassInvul()) {
            int slot = ItemsUtil.getBaubleSlotInMaid(maid, this);
            if (slot >= 0) {
                event.setCanceled(true);
                ItemStack stack = maid.getMaidBauble().getStackInSlot(slot);
                stack.hurtAndBreak(1, maid, m -> maid.sendItemBreakMessage(stack));
                maid.getMaidBauble().setStackInSlot(slot, stack);
                maid.setHealth(maid.getMaxHealth());
                MaidParticlePacket.sendNearby(maid, MaidParticlePacket.Type.HEART);
                maid.playSound(SoundEvents.GLASS_BREAK, 1.0f, 1.0f);
                if (maid.getOwner() instanceof ServerPlayer serverPlayer) {
                    InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.USE_UNDEAD_BAUBLE);
                }
            }
        }
    }
}

