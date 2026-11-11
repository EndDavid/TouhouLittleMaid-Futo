package com.github.tartaricacid.touhoulittlemaid.item.bauble;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDamageEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.network.MaidParticlePacket;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 魔法保护饰品，移植自上游 {@code item/bauble/MagicProtectBauble}。
 *
 * <p>除了挡伤害，还会 {@code removeAllEffects()} —— 上游的意图是
 * 「魔法伤害多半伴随负面效果（虚弱 / 缓慢 / 中毒），挡了伤害却留着 debuff 很怪」。
 * 副作用是连<b>正面</b>效果也一起清掉，这是上游的既定行为，不额外修正。</p>
 */
public class MagicProtectBauble implements IMaidBauble {
    public MagicProtectBauble() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingDamage(MaidDamageEvent event) {
        EntityMaid maid = event.getMaid();
        DamageSource source = event.getSource();
        if (source.isMagic()) {
            int slot = ItemsUtil.getBaubleSlotInMaid(maid, this);
            if (slot >= 0) {
                event.setCanceled(true);
                ItemStack stack = maid.getMaidBauble().getStackInSlot(slot);
                stack.hurtAndBreak(1, maid, m -> maid.sendItemBreakMessage(stack));
                maid.getMaidBauble().setStackInSlot(slot, stack);
                maid.removeAllEffects();
                MaidParticlePacket.sendNearby(maid, MaidParticlePacket.Type.EXPLOSION);
                if (maid.getOwner() instanceof ServerPlayer serverPlayer) {
                    InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.USE_PROTECT_BAUBLE);
                }
            }
        }
    }
}

