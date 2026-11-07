package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * P 点物品，移植自上游 {@code item/ItemPowerPoint}。
 *
 * <p>右击把它<b>丢出去</b>，落地后变成 {@link EntityPowerPoint} 实体，随后可以被
 * 玩家或女仆吸收。上游为了实现「抛物线的飞行过程」额外做了一个投掷物实体
 * {@code EntityThrowPowerPoint}；本移植版把这两步合成一步：直接把 P 点实体按
 * 视线方向赋予初速度丢出（并给它 {@code throwTime} 拾取冷却），
 * 手感一致而少了一个实体类型。</p>
 *
 * <p>物品本身主要来自地牢战利品箱（战利品表已在资源里，但 Forge 的
 * {@code global_loot_modifiers} 机制 Fabric 侧未移植，因此暂时只能靠
 * 击杀掉落获得）。</p>
 */
public class ItemPowerPoint extends Item {
    public ItemPowerPoint() {
        super(new FabricItemSettings()
                .tab(com.github.tartaricacid.touhoulittlemaid.init.InitItems.MAIN_TAB));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_BOTTLE_THROW, SoundSource.NEUTRAL, 0.5F,
                0.4F / (player.getRandom().nextFloat() * 0.4F + 0.8F));

        if (!level.isClientSide) {
            EntityPowerPoint point = EntityPowerPoint.create(level,
                    player.getX(), player.getEyeY() - 0.1D, player.getZ(), 34);
            // 沿视线丢出（与上游 shootFromRotation 的角度一致：俯仰再压低 20 度）
            point.shootFrom(player, -20.0F, 0.7F);
            // 2 秒的拾取冷却，否则丢出去的瞬间就会被自己吸回去
            point.throwTime = 40;
            level.addFreshEntity(point);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
