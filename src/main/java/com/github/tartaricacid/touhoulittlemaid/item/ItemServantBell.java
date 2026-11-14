package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidInfo;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidWorldData;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * 仆从铃，逐字移植自上游 {@code item/ItemServantBell}。
 *
 * <h2>用法（三步）</h2>
 * <ol>
 *     <li>主手拿铃，右击<b>自己的</b>女仆 → 弹出一个输入框，给这枚铃起个提示名
 *         （这一步只写 NBT，不消耗任何东西）；</li>
 *     <li>之后按住右键再松开 → 若这只女仆<b>在已加载区块里</b>，把她传送到身边；</li>
 *     <li>若她<b>在未加载区块里</b>，改为把她的区块坐标写进 NBT，
 *         客户端据此在世界上画一个「N 米」的浮标（见 {@code ServantBellTracker}）。</li>
 * </ol>
 *
 * <h2>与小号的分工</h2>
 * <p>小号是「把<b>所有</b>女仆召回来，但只能召回已加载的」；仆从铃是
 * 「只召回<b>指定的那一只</b>，且能在她所在的区块没加载时告诉你去哪找」。
 * 两者都受同一个硬限制：未加载区块里的实体不在内存里，服务端拿不到。</p>
 *
 * <h2>与上游的差异</h2>
 * <ul>
 *     <li>{@code sendMessage(Component, UUID)} 在 1.18.2 已移除，
 *         改用 {@code displayClientMessage(Component, boolean)}。</li>
 *     <li>{@code EntityMaid.TYPE} 是 Forge 侧常量，本移植版由
 *         {@link InitEntities#MAID} 持有。</li>
 *     <li>上游用 {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)} 开界面；
 *         Fabric 没有 DistExecutor，这里用 {@link FabricLoader#getEnvironmentType()}
 *         判断 + 嵌套的 {@code ClientOnly} 类，服务端永远不会解析
 *         {@code Minecraft} / {@code Screen}（见 {@link ClientOnly}）。</li>
 * </ul>
 */
public class ItemServantBell extends Item {
    private static final String UUID_TAG = "ServantBellUuid";
    private static final String TIP_TAG = "ServantBellTip";
    private static final String SHOW_TAG = "ServantBellShow";
    private static final String SHOW_DIMENSION_TAG = "Dimension";
    private static final String SHOW_POS_TAG = "Pos";

    /**
     * 松手时允许的<b>剩余</b>使用时间下限（tick）。
     *
     * <p><b>注意这不是「最少蓄力时长」。</b>javap 确认 1.18.2 的调用是
     * {@code stack.releaseUsing(level, entity, getUseItemRemainingTicks())} ——
     * 第三个参数是「还剩多久用完」，从 {@link #getUseDuration} 一路递减到 0。
     * 于是 {@code timeLeft < 20} 为真意味着<b>已经按了超过 80 tick</b>。</p>
     *
     * <p>所以这个判据的实际效果是：<b>按下后 4 秒内松手有效，按太久反而无效</b>；
     * 按满 100 tick 时原版根本不会调 {@code releaseUsing}（走的是
     * {@code finishUsingItem}），同样没反应。上游 1.18.2 与 1.20.1 都是这个写法，
     * 这里照抄，没有「修正」成直觉版本 —— 改了会改变手感。</p>
     */
    private static final int MIN_USE_DURATION = 20;

    /**
     * 使用后的冷却（tick）。上游是 20，即 1 秒。
     */
    private static final int COOLDOWN = 20;

    public ItemServantBell() {
        super(new FabricItemSettings().tab(InitItems.MAIN_TAB).stacksTo(1));
    }

    /**
     * 把一枚铃绑定到某只女仆。
     *
     * <p>上游在这里检查 {@code stack.is(InitItems.SERVANT_BELL.get())}；
     * 本移植版改由调用方（{@code ServantBellSetPacket}）用
     * {@code instanceof ItemServantBell} 检查，效果相同。</p>
     */
    public static void recordMaidInfo(ItemStack stack, UUID uuid, String tip) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(UUID_TAG, uuid);
        tag.putString(TIP_TAG, tip);
    }

    /**
     * 读出「她所在的区块坐标 + 维度」，供客户端画跟踪浮标。
     *
     * <p>只在 {@code /tlm} 之外的一个地方用到：{@code ServantBellTracker} 每帧
     * 读主手那枚铃。返回 {@code null} 表示这枚铃没写过坐标（即她当时是加载着的，
     * 已经被直接传过来了）。</p>
     */
    @Nullable
    public static Pair<String, BlockPos> getMaidShow(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(SHOW_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag showTag = tag.getCompound(SHOW_TAG);
            if (showTag.contains(SHOW_DIMENSION_TAG, Tag.TAG_STRING)
                    && showTag.contains(SHOW_POS_TAG, Tag.TAG_COMPOUND)) {
                String dimension = showTag.getString(SHOW_DIMENSION_TAG);
                BlockPos blockPos = NbtUtils.readBlockPos(showTag.getCompound(SHOW_POS_TAG));
                return Pair.of(dimension, blockPos);
            }
        }
        return null;
    }

    /**
     * 主手拿铃右击自己的女仆 → 开输入框。
     *
     * <p><b>必须两端都返回 SUCCESS</b>：服务端返回 SUCCESS 是为了让
     * {@code EntityMaid#mobInteract} 提前收手（它在
     * {@code stack.interactLivingEntity(...)} 返回「已消费」后就不再往下走），
     * 否则会在输入框后面又弹出女仆自己的界面。客户端返回 SUCCESS 是为了
     * 不再触发手上的动作。</p>
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand usedHand) {
        if (usedHand == InteractionHand.MAIN_HAND
                && target instanceof EntityMaid maid && maid.isOwnedByPlayer(player)) {
            if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                ClientOnly.openSetScreen(maid);
            }
            return InteractionResult.SUCCESS;
        }
        return super.interactLivingEntity(stack, player, target, usedHand);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (getMaidUuid(stack) == null) {
            // 没绑定就按住：直接告诉玩家，别让他按了半天才发现没反应

            if (!level.isClientSide) {
                player.displayClientMessage(new TranslatableComponent(
                        "message.touhou_little_maid.servant_bell.data_is_empty"), false);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entityLiving, int timeLeft) {
        if (!(entityLiving instanceof Player player) || timeLeft < MIN_USE_DURATION) {
            return;
        }
        UUID searchUuid = getMaidUuid(stack);
        if (searchUuid == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(new TranslatableComponent(
                        "message.touhou_little_maid.servant_bell.data_is_empty"), false);
            }
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            // getEntities 的返回类型是 List<? extends T>，不能直接赋给 List<EntityMaid>
            List<? extends EntityMaid> maids = serverLevel.getEntities(InitEntities.MAID,
                    maid -> this.checkMaidUuid(player, maid, searchUuid));
            if (maids.isEmpty()) {
                this.showMaidInfo(level, player, stack, searchUuid);
            } else {
                stack.getOrCreateTag().remove(SHOW_TAG);
                this.teleportMaid(player, maids);
            }
        }
        // 音效与进度触发器两端都会走到，与上游一致（上游 playSound 也在 if 之外）
        level.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK,
                SoundSource.BLOCKS, 2.0F, 1.0F);
        if (player instanceof ServerPlayer serverPlayer) {
            InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.USE_SERVANT_BELL);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
    }

    @Nullable
    private UUID getMaidUuid(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID(UUID_TAG)) {
            return tag.getUUID(UUID_TAG);
        }
        return null;
    }

    private void teleportMaid(Player player, List<? extends EntityMaid> maids) {
        maids.forEach(maid -> {
            // 开着「家模式」的话她会立刻往家走，玩家看到的是「刚叫来又自己走了」
            maid.setHomeModeEnable(false);
            // 1.20.1 的这行「先让女仆下来」在 1.18.2 上游没有，但它是必要的：
            // 骑着东西的实体直接 teleportTo 不会跟着挪乘客 / 载具，结果是她的人在
            // 原地、坐垫还在原处。加上之后行为与 1.20.1 一致。
            if (maid.isPassenger()) {
                maid.stopRiding();
            }
            // 发光 10 秒，方便在一堆女仆里认出刚召回来的那只
            maid.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 1, true, false));
            maid.teleportTo(
                    player.getX() + player.getRandom().nextInt(3) - 1,
                    player.getY(),
                    player.getZ() + player.getRandom().nextInt(3) - 1);
        });
    }

    /**
     * 她不在已加载区块里：把她的区块坐标写进 NBT，并说明原因。
     *
     * <p>坐标来自 {@link MaidWorldData} —— 女仆在卸载区块时留档的位置。
     * 没有这份记录就只能说「找不到」。</p>
     */
    private void showMaidInfo(Level level, Player player, ItemStack stack, UUID searchUuid) {
        MaidWorldData data = MaidWorldData.get(level);
        if (data == null) {
            player.displayClientMessage(new TranslatableComponent(
                    "message.touhou_little_maid.servant_bell.no_result"), false);
            return;
        }
        List<MaidInfo> infos = data.getPlayerMaidInfos(player);
        if (infos == null || infos.isEmpty()) {
            player.displayClientMessage(new TranslatableComponent(
                    "message.touhou_little_maid.servant_bell.no_result"), false);
            return;
        }
        MaidInfo info = null;
        for (MaidInfo candidate : infos) {
            if (candidate.getEntityId().equals(searchUuid)) {
                info = candidate;
                break;
            }
        }
        if (info == null) {
            player.displayClientMessage(new TranslatableComponent(
                    "message.touhou_little_maid.servant_bell.no_result"), false);
            return;
        }
        String dimension = info.getDimension();
        String playerDimension = player.level.dimension().location().toString();
        CompoundTag showTag = stack.getOrCreateTagElement(SHOW_TAG);
        showTag.putString(SHOW_DIMENSION_TAG, dimension);
        showTag.put(SHOW_POS_TAG, NbtUtils.writeBlockPos(info.getChunkPos()));
        if (dimension.equals(playerDimension)) {
            player.displayClientMessage(new TranslatableComponent(
                    "message.touhou_little_maid.servant_bell.show_pos"), false);
        } else {
            player.displayClientMessage(new TranslatableComponent(
                    "message.touhou_little_maid.servant_bell.not_same_dimension", dimension), false);
        }
    }

    private boolean checkMaidUuid(Player player, EntityMaid maid, UUID searchUuid) {
        return maid.isOwnedByPlayer(player) && maid.getUUID().equals(searchUuid);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 100;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    /**
     * 绑好之后物品名字直接改成玩家写的提示。
     *
     * <p>上游用 {@code new TranslatableComponent(tip)} —— 也就是把那串文本
     * <b>当成翻译键</b>。照抄的话玩家输入「小白」会显示成
     * {@code 小白}（找不到这个键时 {@code TranslatableComponent} 会原样回显键名），
     * 看起来没问题，但玩家输入的是 {@code item.foo} 这种真键名时会显示成
     * 对应翻译，与他的输入不符。1.20.1 上游已经改成了 {@code Component.literal}，
     * 这里跟 1.20.1。</p>
     */
    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TIP_TAG, Tag.TAG_STRING)) {
            String tip = tag.getString(TIP_TAG);
            return new TextComponent(tip)
                    .withStyle(ChatFormatting.GOLD)
                    .withStyle(ChatFormatting.UNDERLINE);
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        UUID uuid = getMaidUuid(stack);
        if (uuid != null) {
            tooltip.add(new TranslatableComponent(
                    "tooltips.touhou_little_maid.servant_bell.uuid", uuid.toString())
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(new TextComponent(" "));
        }
        tooltip.add(new TranslatableComponent(
                "tooltips.touhou_little_maid.servant_bell.desc.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(new TranslatableComponent(
                "tooltips.touhou_little_maid.servant_bell.desc.2").withStyle(ChatFormatting.GRAY));
    }

    /**
     * 客户端专属代码的隔离层。
     *
     * <p>只有 {@link EnvType#CLIENT} 分支会引用这个类，所以专用服务端
     * 永远不解析它，也就不会去加载 {@code Minecraft} / {@code Screen}。
     * 这是本移植版代替 Forge {@code DistExecutor} 的写法（见 {@code ItemChair}）。</p>
     */
    @Environment(EnvType.CLIENT)
    private static final class ClientOnly {
        private static void openSetScreen(EntityMaid maid) {
            com.github.tartaricacid.touhoulittlemaid.client.screen.ServantBellSetScreen.open(maid);
        }
    }
}
