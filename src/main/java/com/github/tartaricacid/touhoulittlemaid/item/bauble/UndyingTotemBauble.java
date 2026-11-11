package com.github.tartaricacid.touhoulittlemaid.item.bauble;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 原版不死图腾也能当饰品用，移植自上游 {@code item/bauble/UndyingTotemBauble}。
 *
 * <p>效果与玩家手持图腾一致：1 点血复活 + 清空效果 + 再生 / 吸收 / 抗火，
 * 并广播 {@link EntityEvent#TALISMAN_ACTIVATE} 让客户端播放图腾激活动画与音效。
 * 差别在于图腾是<b>消耗品</b>（{@code shrink(1)}，没有耐久条）。</p>
 *
 * <p>之所以把它也登记进 {@code BaubleManager}：上游就是这么做的 ——
 * 玩家不必额外合成模组饰品，靠一只原版图腾就能保住女仆一条命。</p>
 */
public class UndyingTotemBauble implements IMaidBauble {
    public UndyingTotemBauble() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingDamage(MaidDeathEvent event) {
        EntityMaid maid = event.getMaid();
        DamageSource source = event.getSource();
        if (!source.isBypassInvul()) {
            int slot = ItemsUtil.getBaubleSlotInMaid(maid, this);
            if (slot >= 0) {
                maid.getMaidBauble().getStackInSlot(slot).shrink(1);
                event.setCanceled(true);
                maid.setHealth(1.0F);
                maid.removeAllEffects();
                maid.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
                maid.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
                maid.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
                maid.level.broadcastEntityEvent(maid, EntityEvent.TALISMAN_ACTIVATE);
                if (maid.getOwner() instanceof ServerPlayer serverPlayer) {
                    InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.USE_UNDEAD_BAUBLE);
                }
            }
        }
    }
}

