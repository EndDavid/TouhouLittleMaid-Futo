package com.github.tartaricacid.touhoulittlemaid.item.bauble;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 物品磁铁饰品，移植自上游 {@code item/bauble/ItemMagnetBauble}。
 *
 * <p>每 {@value #DELAY} tick 把 {@value #RANGE} 格内的可拾取物一次性吸过来。
 * 它<b>不</b>自己实现拾取逻辑，而是复用女仆本来就有的一套
 * {@code canPickup} / {@code pickupItem} / {@code pickupXPOrb} / {@code pickupPowerPoint}
 * / {@code pickupArrow} —— 因此磁铁的行为与「走到物品旁边捡起」完全一致，
 * 包括背包满了就捡不动。</p>
 *
 * <p>前提是「拾取开关打开 + 已驯服」：磁铁只是扩大拾取范围，不是绕过开关。</p>
 */
public class ItemMagnetBauble implements IMaidBauble {
    private static final int DELAY = 3 * 20;
    private static final int RANGE = 6;

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if (maid.tickCount % DELAY == 0) {
            this.handlePickup(maid);
        }
    }

    private void handlePickup(EntityMaid maid) {
        Level world = maid.level;
        if (maid.isPickup() && maid.isTame()) {
            List<Entity> entities = world.getEntities(maid, maid.getBoundingBox().inflate(RANGE), maid::canPickup);
            if (!entities.isEmpty() && maid.isAlive()) {
                for (Entity entityPickup : entities) {
                    // 如果是物品
                    if (entityPickup instanceof ItemEntity itemEntity) {
                        maid.pickupItem(itemEntity, false);
                    }
                    // 如果是经验
                    if (entityPickup instanceof ExperienceOrb orb) {
                        maid.pickupXPOrb(orb);
                    }
                    // 如果是 P 点
                    if (entityPickup instanceof EntityPowerPoint point) {
                        maid.pickupPowerPoint(point);
                    }
                    // 如果是箭
                    if (entityPickup instanceof AbstractArrow arrow) {
                        maid.pickupArrow(arrow, false);
                    }
                }
                if (maid.getOwner() instanceof ServerPlayer serverPlayer) {
                    InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.USE_ITEM_MAGNET_BAUBLE);
                }
            }
        }
    }
}

