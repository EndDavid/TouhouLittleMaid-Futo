package com.github.tartaricacid.touhoulittlemaid.item.bauble;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDamageEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityExtinguishingAgent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 火焰保护饰品，移植自上游 {@code item/bauble/FireProtectBauble}。
 *
 * <p>挡下之后做两件事：给 300 tick 抗火（否则站在火里下一 tick 又会烧），
 * 并在脚边生成一个灭火剂实体（把身上的火扑灭）。两者缺一不可 ——
 * 只给抗火的话女仆仍然「身上着火」，模型会一直播放着火姿态。</p>
 */
public class FireProtectBauble implements IMaidBauble {
    public FireProtectBauble() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingDamage(MaidDamageEvent event) {
        EntityMaid maid = event.getMaid();
        DamageSource source = event.getSource();
        if (source.isFire()) {
            int slot = ItemsUtil.getBaubleSlotInMaid(maid, this);
            if (slot >= 0) {
                event.setCanceled(true);
                ItemStack stack = maid.getMaidBauble().getStackInSlot(slot);
                stack.hurtAndBreak(1, maid, m -> maid.sendItemBreakMessage(stack));
                maid.getMaidBauble().setStackInSlot(slot, stack);
                maid.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 300));
                if (!maid.level.isClientSide) {
                    maid.level.addFreshEntity(new EntityExtinguishingAgent(maid.level, maid.position()));
                }
                if (maid.getOwner() instanceof ServerPlayer serverPlayer) {
                    InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.USE_PROTECT_BAUBLE);
                }
            }
        }
    }
}

