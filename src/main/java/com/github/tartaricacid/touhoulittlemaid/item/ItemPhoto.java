package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.util.PlaceHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 照片（上游 {@code item/ItemPhoto} 的移植）。
 *
 * <p>由 {@link ItemCamera} 拍出来，NBT 里存着一只女仆的完整数据；
 * 拿在手上右击方块即可把那只女仆重新放出来 —— 这就是「跨维度 / 远距离搬运女仆」的手段。</p>
 *
 * <p>三个与上游一致的细节：</p>
 * <ol>
 *     <li><b>只认方块的上表面</b>（{@code facing != Direction.UP} 直接失败）——
 *         女仆站立位置取的是点击点坐标，侧面/底面点下去她会嵌进墙里；</li>
 *     <li><b>要先检查两格空间</b>（{@link PlaceHelper#notSuitableForPlaceMaid}）；</li>
 *     <li><b>实体创建必须在服务端</b>，客户端只跑粒子与音效，否则会出现两只女仆。</li>
 * </ol>
 */
public class ItemPhoto extends AbstractStoreMaidItem {
    public ItemPhoto() {
        super(new net.fabricmc.fabric.api.item.v1.FabricItemSettings()
                .stacksTo(1)
                .tab(InitItems.MAIN_TAB)
                // 上游靠 Forge 的 onEntityItemUpdate 把掉落物设成无敌来防火；
                // 1.18.2 没有那个钩子，改用物品自带的防火属性（见 AbstractStoreMaidItem 的类注释）。
                .fireResistant());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Direction facing = context.getClickedFace();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack photo = context.getItemInHand();
        Vec3 clickLocation = context.getClickLocation();

        if (player == null) {
            return super.useOn(context);
        }

        // 方向不对，或者上面两格放不下
        if (facing != Direction.UP || PlaceHelper.notSuitableForPlaceMaid(level, pos)) {
            if (level.isClientSide) {
                player.sendMessage(new TranslatableComponent(
                        "message.touhou_little_maid.photo.not_suitable_for_place_maid"), Util.NIL_UUID);
            }
            return InteractionResult.FAIL;
        }

        // 检查照片里到底有没有女仆数据
        if (!hasMaidData(photo)) {
            if (level.isClientSide) {
                player.sendMessage(new TranslatableComponent(
                        "message.touhou_little_maid.photo.have_no_nbt_data"), Util.NIL_UUID);
            }
            return InteractionResult.FAIL;
        }

        // 最后才真正生成实体
        Optional<Entity> entityOptional = EntityType.create(getMaidData(photo), level);
        if (entityOptional.isPresent() && entityOptional.get() instanceof EntityMaid maid) {
            maid.setPos(clickLocation.x, clickLocation.y, clickLocation.z);
            // 实体生成必须在服务端应用
            if (!level.isClientSide) {
                level.addFreshEntity(maid);
            }
            // 粒子两端都放：客户端立刻有反馈，不用等实体同步包
            maid.spawnExplosionParticle();
            photo.shrink(1);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.FAIL;
    }

    /**
     * 不能放进潜影盒等容器。
     *
     * <p>照片的全部价值都在 NBT 里，而容器物品的 NBT 在某些路径上会被丢弃，
     * 一旦丢掉就是「女仆凭空消失」。上游同样禁止。</p>
     */
    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    /**
     * 提示文本。
     *
     * <p>与上游一致：这里<b>只</b>负责「没有数据」那一行。
     * 女仆名字与三维预览由 {@code ClientMaidTooltip} 在提示框里绘制（高度 70 的那一块），
     * 数据来自 {@link AbstractStoreMaidItem#getTooltipImage}。
     * 在这里再贴一次名字会与它重复。</p>
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (!hasMaidData(stack)) {
            tooltip.add(new TranslatableComponent(
                    "tooltips.touhou_little_maid.photo.no_data.desc").withStyle(ChatFormatting.DARK_RED));
        }
    }
}
