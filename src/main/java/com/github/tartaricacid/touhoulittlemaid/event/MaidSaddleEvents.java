package com.github.tartaricacid.touhoulittlemaid.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 「抱着女仆时，拿着鞍右击空气即可放下」（上游 {@code event/CancelSaddleMaidEvent}）。
 *
 * <p><b>为什么单独需要它：</b>用鞍抱起女仆以后，她就在玩家身前 0.75 格。
 * 想放下有两条路 ——</p>
 * <ol>
 *     <li>再右击<b>她本人</b>（走 {@code EntityMaid#saddleMaid} 的第二条分支）；</li>
 *     <li>拿着鞍右击<b>空气或方块</b>（就是这里）。上游补了这条，因为被抱着时
 *         准星经常落在方块上而不是她身上，只靠第一条会很难点。</li>
 * </ol>
 *
 * <p>上游挂在 Forge 的 {@code PlayerInteractEvent.RightClickItem} 上；
 * Fabric 里对应的是 {@link UseItemCallback}，它正好是「右键使用物品」这条路径，
 * 与「右击实体」互不重叠，因此不会和 {@code mobInteract} 抢同一次点击。</p>
 */
public final class MaidSaddleEvents {
    private MaidSaddleEvents() {
    }

    public static void init() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(Items.SADDLE)) {
                return InteractionResultHolder.pass(stack);
            }
            // 只在服务端真正放人：乘客关系由服务端决定，客户端自己做会和服务端打架
            if (level.isClientSide) {
                return InteractionResultHolder.pass(stack);
            }
            if (player.getFirstPassenger() instanceof EntityMaid maid) {
                // 同一次右键里「右击实体」和「使用物品」可能都派发到，
                // 刚抱起的几 tick 内一律不认，否则会立刻被放下来。
                if (maid.tickCount - maid.getSaddleMountTick() > 3) {
                    player.ejectPassengers();
                }
                return InteractionResultHolder.success(stack);
            }
            return InteractionResultHolder.pass(stack);
        });
    }
}

