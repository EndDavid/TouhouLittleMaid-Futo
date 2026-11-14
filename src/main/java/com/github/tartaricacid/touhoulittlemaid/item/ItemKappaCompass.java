package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 河童的罗盘，逐行移植自上游 {@code item/ItemKappaCompass}。
 *
 * <h2>用法（四个动作，按顺序设定）</h2>
 * <ol>
 *     <li><b>右击方块</b>：依次记下工作点 → 娱乐点 → 睡觉点。第二、三个点距前一个
 *         超过 64 格就拒绝（否则三个圈会散落在完全不同的地方，玩家根本看不出关系）；</li>
 *     <li><b>潜行右击方块</b>：清空罗盘里记的坐标；</li>
 *     <li><b>右击女仆</b>：把三个点写进她的 {@code SchedulePos}，之后她开着 Home
 *         就会按日程在三个圈之间活动；</li>
 *     <li><b>潜行右击女仆</b>：清空她的作息坐标（<b>注意它同时会把 Home 关掉</b> ——
 *         上游的 {@code SchedulePos#clear} 会把三个点并回工作点并置
 *         {@code configured = false}，于是下次开 Home 会以她当时的站位重新锚定）。</li>
 * </ol>
 *
 * <h2>数据放在物品 NBT 里</h2>
 * <p>{@code KappaCompassData} 子标签下用 {@link Activity#getName()} 作键
 * （{@code minecraft:work} / {@code minecraft:idle} / {@code minecraft:rest}），
 * 加一个 {@code Dimension}。与上游键名完全一致，所以罗盘可以在两个模组之间被识别。</p>
 *
 * <h2>与上游的差异</h2>
 * <ul>
 *     <li>上游用 {@code player.isDiscrete()}（Forge 给 {@code Player} 加的方法，
 *         语义是「潜行且没骑在载具上」）。vanilla 1.18.2 没有它，这里用
 *         {@link Player#isShiftKeyDown()}。差别只在「骑在船上潜行右键女仆」这一种情况，
 *         而骑乘状态下本来就打不到女仆。</li>
 *     <li>上游 {@code sendMessage(Component, UUID)} 是 Forge 的重载；这里换成
 *         {@code displayClientMessage(Component, false)}（不刷动作栏）。</li>
 * </ul>
 */
public class ItemKappaCompass extends Item {
    private static final String KAPPA_COMPASS_DATA = "KappaCompassData";
    private static final String DIMENSION = "Dimension";

    /**
     * 相邻两个坐标之间允许的最大距离（格）。上游 64。
     */
    private static final int MAX_POINT_DISTANCE = 64;

    public ItemKappaCompass() {
        super(new FabricItemSettings().tab(InitItems.MAIN_TAB).stacksTo(1));
    }

    public static void addPoint(Activity activity, BlockPos pos, ItemStack compass) {
        CompoundTag tag = compass.getOrCreateTagElement(KAPPA_COMPASS_DATA);
        tag.put(activity.getName(), NbtUtils.writeBlockPos(pos));
    }

    public static void addDimension(ResourceLocation dimension, ItemStack compass) {
        CompoundTag tag = compass.getOrCreateTagElement(KAPPA_COMPASS_DATA);
        tag.putString(DIMENSION, dimension.toString());
    }

    /**
     * 取某个活动对应的坐标。
     *
     * <p><b>找不到时按 工作 → 娱乐 的顺序回退</b>，与上游一致。原因是「只设了一个点」
     * 的罗盘在玩家看来就是「所有活动都在这里」，回退让那一个点同时充当三个点。
     * 完全没记录时返回 {@code null}。</p>
     */
    @Nullable
    public static BlockPos getPoint(Activity activity, ItemStack compass) {
        CompoundTag tag = compass.getTagElement(KAPPA_COMPASS_DATA);
        if (tag != null) {
            String name = activity.getName();
            if (tag.contains(name, Tag.TAG_COMPOUND)) {
                return NbtUtils.readBlockPos(tag.getCompound(name));
            }
            name = Activity.IDLE.getName();
            if (tag.contains(name, Tag.TAG_COMPOUND)) {
                return NbtUtils.readBlockPos(tag.getCompound(name));
            }
            name = Activity.WORK.getName();
            if (tag.contains(name, Tag.TAG_COMPOUND)) {
                return NbtUtils.readBlockPos(tag.getCompound(name));
            }
        }
        return null;
    }

    /**
     * 维度 id。
     *
     * <p>与上游的差异：上游写的是 {@code new ResourceLocation(tag.getString("Dimension"))}，
     * 而那个构造函数遇到空串 / 非法字符会<b>抛异常</b>。这段代码同时跑在
     * {@code appendHoverText}（客户端每帧画 tooltip）里，于是「一件被 /give 塞了
     * 半个 KappaCompassData 的罗盘」就能让客户端崩在渲染线程上。
     * 这里改成 {@code tryParse}，解析不出来返回 {@code null}，调用方本来就有判空。</p>
     */
    @Nullable
    public static ResourceLocation getDimension(ItemStack compass) {
        CompoundTag tag = compass.getTagElement(KAPPA_COMPASS_DATA);
        if (tag != null) {
            return ResourceLocation.tryParse(tag.getString(DIMENSION));
        }
        return null;
    }

    /**
     * 已记录几个点。决定下一次右击方块写的是哪个活动。
     */
    public static int getRecordCount(ItemStack compass) {
        CompoundTag tag = compass.getTagElement(KAPPA_COMPASS_DATA);
        int count = 0;
        if (tag != null) {
            if (tag.contains(Activity.WORK.getName(), Tag.TAG_COMPOUND)) {
                count++;
            }
            if (tag.contains(Activity.IDLE.getName(), Tag.TAG_COMPOUND)) {
                count++;
            }
            if (tag.contains(Activity.REST.getName(), Tag.TAG_COMPOUND)) {
                count++;
            }
        }
        return count;
    }

    public static boolean hasKappaCompassData(ItemStack compass) {
        return compass.getTagElement(KAPPA_COMPASS_DATA) != null;
    }

    /**
     * 右击女仆：写入 / 清空她的作息坐标。
     *
     * <p>这条走的是 vanilla 的 {@code Item#interactLivingEntity}，所以和上游一样
     * 由 {@code EntityMaid#mobInteract} 最开头那次调用触发，<b>早于</b>所有
     * 「打开女仆界面」的逻辑。</p>
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack compass, Player player,
                                                  LivingEntity livingEntity, InteractionHand hand) {
        if (livingEntity instanceof EntityMaid maid && !maid.level.isClientSide) {
            if (player.isShiftKeyDown()) {
                maid.getSchedulePos().clear(maid);
                player.displayClientMessage(
                        new TranslatableComponent("message.touhou_little_maid.kappa_compass.maid_clear"), false);
                playCompassSound(player);
                return InteractionResult.SUCCESS;
            }
            CompoundTag tag = compass.getTagElement(KAPPA_COMPASS_DATA);
            ResourceLocation dimension = getDimension(compass);
            if (tag != null || dimension != null) {
                // 坐标只在它自己的维度里有意义：她人在下界，写的却是主世界的坐标，
                // 开了 Home 会被传送到一个不存在的位置（或者直接卡在半空）
                if (!maid.level.dimension().location().equals(dimension)) {
                    player.displayClientMessage(
                            new TranslatableComponent(
                                    "message.touhou_little_maid.kappa_compass.maid_dimension_check"), false);
                    return InteractionResult.CONSUME;
                }
                maid.getSchedulePos().setDimension(dimension);
                BlockPos point = getPoint(Activity.WORK, compass);
                if (point != null) {
                    maid.getSchedulePos().setWorkPos(point);
                }
                point = getPoint(Activity.IDLE, compass);
                if (point != null) {
                    maid.getSchedulePos().setIdlePos(point);
                }
                point = getPoint(Activity.REST, compass);
                if (point != null) {
                    maid.getSchedulePos().setSleepPos(point);
                }
                // configured 必须在写入坐标之后置位：否则下次开 Home 会走
                //「未配置 → 三个点全设成她当前站位」那条路，把罗盘坐标冲掉
                maid.getSchedulePos().setConfigured(true);
                maid.getSchedulePos().restrictTo(maid);
                player.displayClientMessage(
                        new TranslatableComponent("message.touhou_little_maid.kappa_compass.maid_write"), false);
                playCompassSound(player);
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(
                    new TranslatableComponent("message.touhou_little_maid.kappa_compass.no_data"), false);
            return InteractionResult.CONSUME;
        }
        return super.interactLivingEntity(compass, player, livingEntity, hand);
    }

    /**
     * 右击方块：依次写入工作点 / 娱乐点 / 睡觉点；潜行时清空。
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        ItemStack compass = context.getItemInHand();
        BlockPos clickedPos = context.getClickedPos();
        if (player == null || context.getLevel().isClientSide) {
            return super.useOn(context);
        }
        if (player.isShiftKeyDown()) {
            compass.removeTagKey(KAPPA_COMPASS_DATA);
            player.displayClientMessage(
                    new TranslatableComponent("message.touhou_little_maid.kappa_compass.clear"), false);
        } else {
            int recordCount = getRecordCount(compass);
            if (recordCount >= 3) {
                player.displayClientMessage(
                        new TranslatableComponent("message.touhou_little_maid.kappa_compass.full"), false);
            } else if (recordCount == 2) {
                BlockPos idlePos = getPoint(Activity.IDLE, compass);
                if (idlePos != null && idlePos.distSqr(clickedPos) > MAX_POINT_DISTANCE * MAX_POINT_DISTANCE) {
                    player.displayClientMessage(
                            new TranslatableComponent("message.touhou_little_maid.kappa_compass.far_away"), false);
                    return super.useOn(context);
                }
                addPoint(Activity.REST, clickedPos, compass);
                player.displayClientMessage(new TranslatableComponent(
                        "message.touhou_little_maid.kappa_compass.sleep",
                        clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()), false);
            } else if (recordCount == 1) {
                BlockPos workPos = getPoint(Activity.WORK, compass);
                if (workPos != null && workPos.distSqr(clickedPos) > MAX_POINT_DISTANCE * MAX_POINT_DISTANCE) {
                    player.displayClientMessage(
                            new TranslatableComponent("message.touhou_little_maid.kappa_compass.far_away"), false);
                    return super.useOn(context);
                }
                addPoint(Activity.IDLE, clickedPos, compass);
                player.displayClientMessage(new TranslatableComponent(
                        "message.touhou_little_maid.kappa_compass.idle",
                        clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()), false);
            } else {
                addPoint(Activity.WORK, clickedPos, compass);
                player.displayClientMessage(new TranslatableComponent(
                        "message.touhou_little_maid.kappa_compass.work",
                        clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()), false);
            }
            addDimension(player.level.dimension().location(), compass);
        }
        // 物品 NBT 变了要主动同步：否则客户端手里那件罗盘要等下一次背包全量同步
        // 才更新，表现是「点完方块，预览圈要愣几秒才冒出来」。
        // 仆从铃写字标时也是同一处理（见 ItemServantBell）。
        player.inventoryMenu.broadcastChanges();
        playCompassSound(player);
        return InteractionResult.SUCCESS;
    }

    /**
     * 提示音（{@code item.compass}，音量 0.8、音调 1.5）—— 与上游逐字一致。
     *
     * <p>{@code SoundSource.PLAYERS} 而不是 {@code BLOCKS}：上游把它归在玩家音效里，
     * 这样「玩家音效」音量条能压住它。</p>
     */
    private static void playCompassSound(Player player) {
        player.level.playSound(null, player.blockPosition(), InitSounds.COMPASS_POINT,
                SoundSource.PLAYERS, 0.8F, 1.5F);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components,
                                TooltipFlag isAdvanced) {
        if (hasKappaCompassData(stack)) {
            // 已记录的三个点：颜色与上游一致（工作红 / 娱乐绿 / 睡觉蓝），
            // 与世界上那三个圈的颜色对应，玩家不用记也能对上号
            ResourceLocation dimension = getDimension(stack);
            BlockPos workPos = getPoint(Activity.WORK, stack);
            BlockPos idlePos = getPoint(Activity.IDLE, stack);
            BlockPos sleepPos = getPoint(Activity.REST, stack);
            if (dimension != null) {
                components.add(new TranslatableComponent(
                        "tooltips.touhou_little_maid.fox_scroll.dimension", dimension.toString())
                        .withStyle(ChatFormatting.GOLD));
            }
            if (workPos != null) {
                components.add(new TranslatableComponent(
                        "message.touhou_little_maid.kappa_compass.work",
                        workPos.getX(), workPos.getY(), workPos.getZ()).withStyle(ChatFormatting.RED));
            }
            if (idlePos != null) {
                components.add(new TranslatableComponent(
                        "message.touhou_little_maid.kappa_compass.idle",
                        idlePos.getX(), idlePos.getY(), idlePos.getZ()).withStyle(ChatFormatting.GREEN));
            }
            if (sleepPos != null) {
                components.add(new TranslatableComponent(
                        "message.touhou_little_maid.kappa_compass.sleep",
                        sleepPos.getX(), sleepPos.getY(), sleepPos.getZ()).withStyle(ChatFormatting.BLUE));
            }
            components.add(TextComponent.EMPTY);
        }
        components.add(new TranslatableComponent(
                "message.touhou_little_maid.kappa_compass.usage.set_pos"));
        components.add(new TranslatableComponent(
                "message.touhou_little_maid.kappa_compass.usage.clear_pos"));
        components.add(new TranslatableComponent(
                "message.touhou_little_maid.kappa_compass.usage.write_pos_to_maid"));
        components.add(new TranslatableComponent(
                "message.touhou_little_maid.kappa_compass.usage.clear_maid_pos"));
    }
}

