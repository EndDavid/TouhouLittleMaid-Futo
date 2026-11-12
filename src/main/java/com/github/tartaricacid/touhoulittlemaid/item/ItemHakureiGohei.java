package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.api.block.IMultiBlock;
import com.github.tartaricacid.touhoulittlemaid.block.multiblock.MultiBlockManager;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.List;
import java.util.function.Predicate;

/**
 * 御币，移植自上游 {@code item/ItemHakureiGohei}。
 *
 * <p>{@code hakurei_gohei} 与 {@code sanae_gohei} 都是这个类的实例（与上游一致），
 * 差异只体现在物品模型 / 贴图上 —— 两者的资源本移植版已经具备。</p>
 *
 * <p>女仆主手持御币、且工作模式为 {@code danmaku_attack} 时会发射弹幕
 * （见 {@code TaskDanmakuAttack}）。玩家自己使用时除了近战还有第二个用途：
 * <b>对着红羊毛右键搭祭坛</b>（见 {@link #useOn}）。</p>
 */
public class ItemHakureiGohei extends ProjectileWeaponItem {
    /**
     * 主手攻击力 +4、攻速 -2，与上游一致。
     */
    private final Multimap<Attribute, AttributeModifier> defaultModifiers;

    public ItemHakureiGohei() {
        super(new FabricItemSettings().tab(InitItems.MAIN_TAB).durability(300));
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                BASE_ATTACK_DAMAGE_UUID, "Tool modifier", 4.0D, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                BASE_ATTACK_SPEED_UUID, "Tool modifier", -2.0D, AttributeModifier.Operation.ADDITION));
        this.defaultModifiers = builder.build();
    }

    public static boolean isGohei(ItemStack stack) {
        return stack.getItem() instanceof ItemHakureiGohei;
    }

    /**
     * 御币不消耗弹药，因此接受任何物品（弹幕是「凭空」生成的）。
     */
    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return Predicates.alwaysTrue();
    }

    @Override
    public int getDefaultProjectileRange() {
        return 15;
    }

    /**
     * 拉弓时长上限（500 tick）。{@code TaskDanmakuAttack} 复用了
     * {@code MaidShootTargetTask}，它按「使用物品的 tick 数」决定蓄力，
     * 因此这个值要足够大，不能像弓那样 72000（原版弓是 72000，这里上游给的是 500）。
     */
    @Override
    public int getUseDuration(ItemStack stack) {
        return 500;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    /**
     * 右键红羊毛 → 尝试搭建多方块结构（祭坛）。逐字移植自上游。
     *
     * <p>流程：遍历 {@code MultiBlockManager} 注册的多方块定义，找「这一格是核心方块
     * 且这个朝向合法」的那一个，然后把模板起点算出来（{@code 点击位置 + getCenterPos}），
     * 逐格比对方块（{@link IMultiBlock#isMatch}），全部对上才真正替换成祭坛。
     * 只把最顶层的「搭成了」写进 {@code BUILD_ALTAR} 判据，失败什么也不做 ——
     * 手里有御币却对着羊毛右键没反应，就是「摆错了」的唯一提示（与上游一致）。</p>
     *
     * <p>注意 {@code isMatch} 必须在<b>服务端</b>执行：客户端不该替服务端决定结构是否成立，
     * 所以整段逻辑都在 {@code world instanceof ServerLevel} 里。</p>
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getHand() == InteractionHand.MAIN_HAND) {
            List<IMultiBlock> multiBlockList = MultiBlockManager.getMultiBlockList();
            Level world = context.getLevel();
            BlockPos pos = context.getClickedPos();
            BlockState blockState = world.getBlockState(pos);
            Direction direction = context.getClickedFace();

            for (IMultiBlock multiBlock : multiBlockList) {
                if (multiBlock.isCoreBlock(blockState) && multiBlock.directionIsSuitable(direction)) {
                    if (world instanceof ServerLevel serverLevel) {
                        BlockPos posStart = pos.offset(multiBlock.getCenterPos(direction));
                        StructureTemplate template = multiBlock.getTemplate(serverLevel, direction);
                        if (multiBlock.isMatch(world, posStart, direction, template)) {
                            multiBlock.build(world, posStart, direction, template);
                            if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                                InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.BUILD_ALTAR);
                            }
                        }
                    }
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return super.useOn(context);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(2, attacker, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? this.defaultModifiers : super.getDefaultAttributeModifiers(slot);
    }
}

