package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.MaidRayTraceHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 文文的相机（上游 {@code item/ItemCamera} 的移植）。
 *
 * <h2>功能</h2>
 *
 * <p>对本模组的女仆「拍一张照」：把她从世界上收走，并在原地掉落一张
 * {@link ItemPhoto}，照片里存着她完整的存档数据。之后拿照片右击方块即可放出来 ——
 * 这是跨维度 / 远距离搬运女仆的手段（也正是 mcmod 上写的效果）。</p>
 *
 * <h2>三条判定</h2>
 *
 * <ul>
 *     <li>只认<b>自己的</b>女仆（{@code isOwnedByPlayer}）；</li>
 *     <li>必须<b>活着且不在睡觉</b> —— 睡着的女仆不响应交互，强行收走会留下一个
 *         已经绑定床位的空壳；</li>
 *     <li>用 {@link MaidRayTraceHelper} 判断「视线是否真的对准她」，因为
 *         {@code Minecraft#hitResult} 是客户端独有的，而收人要跑在服务端。</li>
 * </ul>
 *
 * <h2>与上游的差异</h2>
 *
 * <ul>
 *     <li><b>没有成就触发器。</b>上游最后会
 *         {@code InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.PHOTO_MAID)}，
 *         那套 Forge 触发器体系（{@code InitTrigger} / {@code TriggerType} /
 *          advancement JSON）尚未移植。</li>
 *     <li><b>去掉了拍照时关闭居家模式以外的副作用。</b>上游这里还会清作息点，
 *         但那属于 {@code SchedulePos}，本移植版用 {@code DATA_HOME_POS} 表达，
 *         由两端的「居家模式」按钮管理，相机只负责关掉开关。</li>
 * </ul>
 */
public class ItemCamera extends Item {
    private static final String MAID_INFO = AbstractStoreMaidItem.MAID_INFO;
    private static final String ID_TAG = "id";
    /**
     * 拍照后镜头冷却（tick）。上游 20，等于 1 秒 —— 防止连点一次拍走一群。
     */
    private static final int COOLDOWN_TICKS = 20;
    /**
     * 视线检索半径（方块）。上游 8。
     */
    private static final int SEARCH_DISTANCE = 8;

    public ItemCamera() {
        super(new net.fabricmc.fabric.api.item.v1.FabricItemSettings()
                .stacksTo(1)
                .durability(50)
                .tab(InitItems.MAIN_TAB));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            ItemStack camera = player.getItemInHand(hand);
            Optional<EntityMaid> result = MaidRayTraceHelper.rayTraceMaid(player, SEARCH_DISTANCE);
            if (result.isPresent()) {
                EntityMaid maid = result.get();
                if (!level.isClientSide && maid.isAlive() && maid.isOwnedByPlayer(player) && !maid.isSleeping()) {
                    this.spawnMaidPhoto(level, maid, player);
                    maid.discard();
                    player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
                    // 相机有 50 点耐久，拍一张掉 1 点
                    camera.hurtAndBreak(1, player,
                            broken -> broken.broadcastBreakEvent(InteractionHand.MAIN_HAND));
                }
                // 粒子在两端都放：客户端立刻看到，不必等实体移除包
                maid.spawnExplosionParticle();
                player.playSound(InitSounds.CAMERA_USE, 1.0F, 1.0F);
                return InteractionResultHolder.sidedSuccess(camera, level.isClientSide);
            }
        }
        return super.use(level, player, hand);
    }

    /**
     * 把女仆写进一张照片并丢到玩家脚下。
     *
     * <p>{@code setHomeModeEnable(false)} 是必需的：照片会把整个 NBT 一起带走，
     * 若居家模式还开着，放出来之后她会立刻被工作范围拽回去，
     * 表现就是「刚放出来就自己走回老家」。上游同款处理。</p>
     */
    private void spawnMaidPhoto(Level level, EntityMaid maid, Player player) {
        ItemStack photo = InitItems.PHOTO.getDefaultInstance();
        CompoundTag photoTag = new CompoundTag();
        CompoundTag maidTag = new CompoundTag();
        maid.setHomeModeEnable(false);
        maid.saveWithoutId(maidTag);
        // saveWithoutId 不含实体 id，而 EntityType.create 需要它才能还原
        ResourceLocation maidId = Registry.ENTITY_TYPE.getKey(InitEntities.MAID);
        if (maidId == null) {
            // 注册表里查不到（理论上不可能）—— 宁可不拍照，也不要丢一张坏照片出去
            return;
        }
        maidTag.putString(ID_TAG, maidId.toString());
        photoTag.put(MAID_INFO, maidTag);
        photo.setTag(photoTag);
        Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), photo);
    }

    /**
     * 直接右击女仆本体也能拍照，并且<b>吃掉这次交互</b>。
     *
     * <p>不吃掉的话会继续走到 {@code EntityMaid#mobInteract} 的「空手右键开界面」分支，
     * 结果就是「拍照的同时弹出了女仆界面」。上游同款处理。</p>
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (stack.getItem() == this && target.isAlive()
                && target instanceof EntityMaid maid && maid.isOwnedByPlayer(player)) {
            this.use(player.level, player, hand);
            return InteractionResult.SUCCESS;
        }
        return super.interactLivingEntity(stack, player, target, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(new TranslatableComponent(
                "tooltips.touhou_little_maid.camera.desc").withStyle(ChatFormatting.DARK_GREEN));
    }
}

