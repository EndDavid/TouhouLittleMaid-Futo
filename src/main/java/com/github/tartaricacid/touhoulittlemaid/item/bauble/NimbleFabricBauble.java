package com.github.tartaricacid.touhoulittlemaid.item.bauble;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAttackEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.github.tartaricacid.touhoulittlemaid.util.TeleportHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.IndirectEntityDamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 机敏织物饰品，移植自上游 {@code item/bauble/NimbleFabricBauble}。
 *
 * <p>挨到<b>间接</b>伤害（{@link IndirectEntityDamageSource}：箭、火球、投掷物，
 * 即「有发射者的投射物」）时，取消这次伤害并随机瞬移逃跑。近战伤害不触发 ——
 * 上游的定位是「躲远程」，不是「无敌」。</p>
 *
 * <p>最多试 {@value #MAX_RETRY} 次瞬移：{@link TeleportHelper#teleport} 会因为
 * 「落点是水 / 落点上方没有实心方块」而失败，单次失败并不罕见，
 * 重试是上游的既定做法。</p>
 */
public class NimbleFabricBauble implements IMaidBauble {
    private static final int MAX_RETRY = 16;

    public NimbleFabricBauble() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingDamage(MaidAttackEvent event) {
        EntityMaid maid = event.getMaid();
        DamageSource source = event.getSource();
        if (source instanceof IndirectEntityDamageSource) {
            int slot = ItemsUtil.getBaubleSlotInMaid(maid, this);
            if (slot >= 0) {
                event.setCanceled(true);
                ItemStack stack = maid.getMaidBauble().getStackInSlot(slot);
                stack.hurtAndBreak(1, maid, m -> maid.sendItemBreakMessage(stack));
                maid.getMaidBauble().setStackInSlot(slot, stack);
                for (int i = 0; i < MAX_RETRY; ++i) {
                    if (TeleportHelper.teleport(maid)) {
                        if (maid.getOwner() instanceof ServerPlayer serverPlayer) {
                            InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.USE_NIMBLE_FABRIC);
                        }
                        return;
                    }
                }
            }
        }
    }
}

