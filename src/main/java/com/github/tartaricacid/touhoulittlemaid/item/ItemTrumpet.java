package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidInfo;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidWorldData;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 小号，逐字移植自上游 {@code item/ItemTrumpet}。
 *
 * <p>按住右键蓄力（{@link #getUseDuration} 100 tick，蓄力动作 {@link UseAnim#BOW}），
 * 松手时如果<b>还没按满 80 tick</b>，就把<b>已加载区块里</b>自己所有的女仆传送到身边，
 * 并给 200 tick（10 秒）冷却。注意「按太久反而无效」是上游的原始写法，
 * 见 {@link #MIN_USE_DURATION} 的说明。</p>
 *
 * <h2>为什么「已加载区块」是硬限制</h2>
 * <p>并非偷懒：未加载区块里的实体根本不存在于内存中，服务端拿不到它们。
 * 上游因此另做了红狐狸卷轴（记录坐标供玩家自己找过去）。所以松手时还会查一次
 * {@link MaidWorldData}，如果记录里还有未加载区块上的女仆，就用
 * {@code trumpet.unloaded_maid} 这条消息明确告诉玩家「还有 N 只在别处」——
 * 否则玩家会以为工具坏了。</p>
 *
 * <h2>与上游的差异</h2>
 * <ul>
 *     <li>{@code sendMessage(Component, UUID)} 在 1.18.2 已不存在，改用
 *         {@code displayClientMessage}。</li>
 *     <li>{@code Entity#teleportTo(x, y, z)} 在 1.18.2 是存在的，直接照抄。</li>
 * </ul>
 */
public class ItemTrumpet extends Item {
    /**
     * 松手时允许的<b>剩余</b>使用时间下限（tick）。
     *
     * <p><b>这个名字容易误导，它不是「最少蓄力时长」。</b>javap 确认 1.18.2 传进来的是
     * {@code stack.releaseUsing(level, entity, getUseItemRemainingTicks())}，
     * 也就是「还剩多久用完」，从 {@link #getUseDuration} 递减到 0。因此
     * {@code timeLeft < 20} 为真意味着<b>已经按了超过 80 tick</b>。</p>
     *
     * <p>实际手感：按下后 4 秒内松手有效，按太久反而无效；按满 100 tick 时原版
     * 根本不调 {@code releaseUsing}（走 {@code finishUsingItem}），同样没反应。
     * 上游 1.18.2 与 1.20.1 都是这个写法，照抄，不改成直觉版本。</p>
     */
    private static final int MIN_USE_DURATION = 20;

    /**
     * 吹完之后的冷却（tick）。10 秒。
     */
    private static final int COOLDOWN = 200;

    public ItemTrumpet() {
        super(new FabricItemSettings().tab(InitItems.MAIN_TAB).stacksTo(1));
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entityLiving, int timeLeft) {
        // timeLeft 是「剩余可用时间」：开始是 getUseDuration()，越蓄越小。
        // 所以 timeLeft < 20 意味着「已经按了超过 80 tick」，而不是「蓄得不够」。
        // 详见 MIN_USE_DURATION 的说明；上游就是这个写法，保持一致。
        if (!(entityLiving instanceof Player player) || timeLeft < MIN_USE_DURATION) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            // EntityMaid.TYPE 是上游 Forge 侧的常量，本移植版由 InitEntities.MAID 持有
            serverLevel.getEntities(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.MAID,
                            Entity::isAlive).stream()
                    .filter(maid -> maid.isOwnedBy(player))
                    .forEach(maid -> this.teleportToOwner(maid, player));
            MaidWorldData data = MaidWorldData.get(level);
            if (data != null) {
                // 只数「区块确实没加载」的那些：本移植版的记录是周期性刷新的，
                // 刚被传送走的这几只也仍有记录，不过滤就会一直提示「还有 N 只在别处」。
                List<MaidInfo> infos = data.getUnloadedMaidInfos(level, player);
                if (!infos.isEmpty()) {
                    player.displayClientMessage(new TranslatableComponent(
                            "message.touhou_little_maid.trumpet.unloaded_maid", infos.size())
                            .withStyle(ChatFormatting.DARK_RED), false);
                }
            }
        }
        if (player instanceof ServerPlayer serverPlayer) {
            InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.USE_TRUMPET);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
    }

    /**
     * 把一只女仆挪到玩家身边（±1 格随机偏移，避免全部叠在同一个坐标里）。
     *
     * <p>先 {@code setHomeModeEnable(false)}：否则开了「家模式」的女仆会立刻往家走，
     * 玩家看到的是「刚召回来又自己走开了」。</p>
     */
    private void teleportToOwner(EntityMaid maid, Player player) {
        maid.setHomeModeEnable(false);
        maid.teleportTo(
                player.getX() + player.getRandom().nextInt(3) - 1,
                player.getY(),
                player.getZ() + player.getRandom().nextInt(3) - 1);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 100;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(new TranslatableComponent("tooltips.touhou_little_maid.trumpet.desc.usage")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(new TranslatableComponent("tooltips.touhou_little_maid.trumpet.desc.note")
                .withStyle(ChatFormatting.DARK_RED));
    }
}
