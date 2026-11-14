package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.block.BlockStatue;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityStatue;
import com.google.common.collect.Lists;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 凿子，逐字移植自上游 {@code item/ItemChisel}。
 *
 * <p>用法：<b>副手拿照片</b>、主手拿凿子，对着粘土方块右键 ——
 * 会以那块粘土为底、往外长出一座雕像，女仆的样子取自照片里的数据。
 * 体积（1×1×1 / 1×2×1 / 2×4×2 / 3×6×3）由周围<b>全是粘土</b>的范围决定，
 * 从大到小试，第一个放得下的尺寸胜出。</p>
 *
 * <h2>耐久消耗</h2>
 * <p>{@code size.ordinal() + 1} —— 迷你 1 点、小 2 点、中 3 点、大 4 点。
 * 上限 64，所以一把凿子能凿 16 座大雕像。</p>
 *
 * <h2>与上游的差异</h2>
 * <ul>
 *     <li>上游在成功后会触发进度触发器 {@code TriggerType.CHISEL_STATUE}；
 *         本移植版的进度触发器子系统尚未完整移植，这里先不接（见 README「已知问题」）。</li>
 *     <li>提示消息用 {@code displayClientMessage} 而不是上游的
 *         {@code sendMessage(Component, UUID)} —— 后者在 1.18.2 已不存在。</li>
 * </ul>
 */
public class ItemChisel extends Item {
    public ItemChisel() {
        super(new FabricItemSettings().tab(InitItems.MAIN_TAB).stacksTo(1).durability(64));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (context.getHand() == InteractionHand.MAIN_HAND && player != null) {
            if (level.getBlockState(pos).getBlock() != Blocks.CLAY) {
                if (!level.isClientSide) {
                    player.displayClientMessage(
                            new TranslatableComponent("message.touhou_little_maid.chisel.hit_block_error"), false);
                }
                return InteractionResult.PASS;
            }
            if (player.getOffhandItem().getItem() != InitItems.PHOTO) {
                if (!level.isClientSide) {
                    player.displayClientMessage(
                            new TranslatableComponent("message.touhou_little_maid.chisel.offhand_not_photo"), false);
                }
                return InteractionResult.PASS;
            }
            this.genStatueBlocks(player, level, pos, context.getClickedFace());
            return InteractionResult.SUCCESS;
        }
        return super.useOn(context);
    }

    /**
     * 从最大的尺寸开始试，第一个「周围全是粘土」的尺寸胜出。
     *
 * <p>全部尺寸都放不下时静默失败（上游也是如此）：玩家看到的是「凿子没反应」，
     * 因为最可能的原因就是粘土不够大。</p>
     */
    private void genStatueBlocks(@Nonnull Player player, @Nonnull Level level, @Nonnull BlockPos pos,
                                 @Nonnull Direction facing) {
        CompoundTag data = ItemPhoto.getMaidData(player.getOffhandItem());
        TileEntityStatue.Size[] sizes = TileEntityStatue.Size.values();
        for (int i = sizes.length - 1; i >= 0; i--) {
            TileEntityStatue.Size size = sizes[i];
            Vec3i dimension = size.getDimension();
            BlockPos[] posList = this.checkBlocks(level, pos, dimension, facing);
            if (posList == null) {
                continue;
            }
            boolean isTiny = posList.length == 1;
            for (BlockPos posIn : posList) {
                level.setBlock(posIn,
                        InitBlocks.STATUE.defaultBlockState().setValue(BlockStatue.IS_TINY, isTiny),
                        Block.UPDATE_ALL);
                BlockEntity te = level.getBlockEntity(posIn);
                if (te instanceof TileEntityStatue statue) {
                    if (posIn.equals(pos)) {
                        statue.setForgeData(size, true, pos, facing, Lists.newArrayList(posList), data);
                    } else {
                        statue.setForgeData(size, false, pos, facing, Lists.newArrayList(posList), null);
                    }
                }
            }
            // 耐久按尺寸递增（迷你 1 → 大 4），坏掉时广播主手
            player.getMainHandItem().hurtAndBreak(size.ordinal() + 1, player,
                    e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            player.playSound(SoundEvents.ANVIL_LAND, 0.5F, 1.5F);
            return;
        }
    }

    /**
     * 按朝向算出这块体积占用的所有坐标，并检查每一格都是粘土。
     *
     * @return 全部合格时返回坐标数组（第一格永远是 {@code origin}），否则 {@code null}
     */
    @Nullable
    private BlockPos[] checkBlocks(@Nonnull Level level, BlockPos origin, Vec3i dimension, Direction facing) {
        BlockPos[] posList = new BlockPos[dimension.getX() * dimension.getY() * dimension.getZ()];
        int index = 0;
        for (int x = 0; x < dimension.getX(); x++) {
            for (int y = 0; y < dimension.getY(); y++) {
                for (int z = 0; z < dimension.getZ(); z++) {
                    BlockPos pos;
                    // 四个水平朝向各有一套「往外长」的方向，保证模型不会被长到玩家身后
                    switch (facing) {
                        case WEST -> pos = origin.offset(x, y, z);
                        case SOUTH -> pos = origin.offset(x, y, -z);
                        case EAST -> pos = origin.offset(-x, y, -z);
                        // UP / DOWN / NORTH：朝北（以及上下表面）默认往 -X 方向长
                        default -> pos = origin.offset(-x, y, z);
                    }
                    posList[index] = pos;
                    index++;
                    if (level.getBlockState(pos).getBlock() != Blocks.CLAY) {
                        return null;
                    }
                }
            }
        }
        return posList;
    }
}

