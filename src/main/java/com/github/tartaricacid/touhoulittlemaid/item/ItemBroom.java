package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 扫帚物品，移植自上游 {@code item/ItemBroom}。
 *
 * <p>它不放置方块，而是<b>摆出一个实体</b>：对着方块右击时先按实体体积算一次包围盒，
 * 只有在「不与方块碰撞、且该处没有其它实体」时才生成 {@link EntityBroom}。
 * 这个检查不是多余的 —— 少了它，扫帚会卡进墙里，而它是 {@code LivingEntity}，
 * 卡进墙里的下一步就是被挤出世界。</p>
 *
 * <p><b>与上游的差异：</b>上游用 {@code EntityBroom.TYPE.create(...)} 这个 8 参重载，
 * 1.18.2 里它确实存在（只是签名是实例方法而不是静态方法），所以这里原样保留。
 * 创造模式物品栏换成了本移植版的 {@link InitItems#MAIN_TAB}。</p>
 */
public class ItemBroom extends Item {
    public ItemBroom() {
        super(new FabricItemSettings().stacksTo(1).tab(InitItems.MAIN_TAB));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.DOWN) {
            Level world = context.getLevel();
            BlockPos clickedPos = new BlockPlaceContext(context).getClickedPos();
            AABB boundingBox = InitEntities.BROOM.getDimensions()
                    .makeBoundingBox(Vec3.atBottomCenterOf(clickedPos));
            if (world.noCollision(boundingBox) && world.getEntities(null, boundingBox).isEmpty()) {
                ItemStack stack = context.getItemInHand();
                if (world instanceof ServerLevel serverWorld) {
                    Component customName = stack.hasCustomHoverName() ? stack.getDisplayName() : null;
                    EntityBroom broom = InitEntities.BROOM.create(serverWorld, stack.getTag(), customName,
                            context.getPlayer(), context.getClickedPos(), MobSpawnType.SPAWN_EGG, true, true);
                    if (broom == null) {
                        return InteractionResult.FAIL;
                    }
                    if (context.getPlayer() != null) {
                        broom.setOwnerUUID(context.getPlayer().getUUID());
                    }
                    world.addFreshEntity(broom);
                    world.playSound(null, broom.getX(), broom.getY(), broom.getZ(),
                            SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.75F, 0.8F);
                }
                stack.shrink(1);
                return InteractionResult.sidedSuccess(world.isClientSide);
            }
        }
        return InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        tooltipComponents.add(new TranslatableComponent("tooltips.touhou_little_maid.broom.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}

