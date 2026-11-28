package com.github.tartaricacid.touhoulittlemaid.block;

import com.github.tartaricacid.touhoulittlemaid.capability.IPower;
import com.github.tartaricacid.touhoulittlemaid.capability.PowerCapability;
import com.github.tartaricacid.touhoulittlemaid.crafting.AltarRecipe;
import com.github.tartaricacid.touhoulittlemaid.init.InitRecipes;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.inventory.AltarRecipeInventory;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityAltar;
import com.github.tartaricacid.touhoulittlemaid.util.PosListData;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * 祭坛方块，移植自上游 {@code block/BlockAltar}。
 */
public class BlockAltar extends Block implements EntityBlock {
    public BlockAltar() {
        super(BlockBehaviour.Properties.of(Material.STONE).strength(2, 2).noOcclusion());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityAltar(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level worldIn, BlockPos pos, Player player,
                                 InteractionHand handIn, BlockHitResult hit) {
        return this.getAltar(worldIn, pos)
                .filter(altar -> handIn == InteractionHand.MAIN_HAND)
                .map(altar -> {
                    if (player.isShiftKeyDown() || player.getMainHandItem().isEmpty()) {
                        takeOutItem(worldIn, altar, player);
                    } else {
                        takeInOrCraft(worldIn, altar, player);
                    }
                    altar.refresh();
                    return InteractionResult.sidedSuccess(worldIn.isClientSide);
                })
                .orElseGet(() -> super.use(state, worldIn, pos, player, handIn, hit));
    }

    /**
     * 方块被移除（含被玩家挖掉、被爆炸炸掉、被其他方块替换）时把贡品吐出来。
     *
     * <p>注意这里<b>不</b>负责还原结构：玩家挖掘走 {@link #playerWillDestroy}，
     * 爆炸走 {@link #wasExploded}，两者都早于本方法。</p>
     */
    @Override
    public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!worldIn.isClientSide) {
            this.getAltar(worldIn, pos).ifPresent(altar -> {
                ItemStack stack = altar.handler.getStackInSlot(0);
                if (!stack.isEmpty()) {
                    Block.popResource(worldIn, pos.offset(0, 1, 0), stack);
                }
            });
        }
        super.onRemove(state, worldIn, pos, newState, isMoving);
    }

    @Override
    public void wasExploded(Level world, BlockPos pos, Explosion explosion) {
        if (!world.isClientSide) {
            this.getAltar(world, pos).ifPresent(altar -> this.restoreStorageBlock(world, pos, altar.getBlockPosList()));
        }
        super.wasExploded(world, pos, explosion);
    }

    /**
     * 玩家挖掘：先把其余祭坛格还原成建坛前的方块，再（非创造时）把「原本那个方块」作为掉落物。
     */
    @Override
    public void playerWillDestroy(Level worldIn, BlockPos pos, BlockState state, Player player) {
        if (!worldIn.isClientSide) {
            this.getAltar(worldIn, pos).ifPresent(altar -> {
                this.restoreStorageBlock(worldIn, pos, altar.getBlockPosList());
                if (!player.isCreative()) {
                    Block block = altar.getStorageState().getBlock();
                    Block.popResource(worldIn, pos, new ItemStack(block));
                }
            });
        }
        super.playerWillDestroy(worldIn, pos, state, player);
    }

    /**
     * 创造模式中键取方块时给「原本那个方块」，而不是祭坛本身 —— 祭坛没有物品形态。
     *
     * <p>1.18.2 的签名是「三参」的 {@code getCloneItemStack(BlockGetter, BlockPos, BlockState)}；
     * 上游（1.20.1）那个带 {@code HitResult} / {@code Player} 的五参重载到 1.20 才出现。</p>
     */
    @Override
    public ItemStack getCloneItemStack(BlockGetter world, BlockPos pos, BlockState state) {
        return this.getAltar(world, pos)
                .map(altar -> new ItemStack(altar.getStorageState().getBlock()))
                .orElseGet(() -> super.getCloneItemStack(world, pos, state));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter worldIn, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public SoundType getSoundType(BlockState state) {
        // 见类注释第 3 条：拿不到坐标，无法回落到原本方块的音效
        return SoundType.STONE;
    }

    private void restoreStorageBlock(Level worldIn, BlockPos currentPos, PosListData posList) {
        for (BlockPos storagePos : posList.getData()) {
            if (storagePos.equals(currentPos)) {
                continue;
            }
            this.getAltar(worldIn, storagePos).ifPresent(
                    altar -> worldIn.setBlock(storagePos, altar.getStorageState(), Block.UPDATE_ALL));
        }
        // 上游 1.20.1 在这里补了一声「信标熄灭」，1.18.2 没有。
        worldIn.playSound(null, currentPos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5f, 1);
    }

    private void takeOutItem(Level world, TileEntityAltar altar, Player player) {
        if (altar.isCanPlaceItem() && !altar.handler.getStackInSlot(0).isEmpty()) {
            ItemStack extractItem = altar.handler.extractItem(0, 1, false);
            ItemHandlerHelper.giveItemToPlayer(player, extractItem);
            // 物品展示框同款「取下」音效，1.20.1 新增
            world.playSound(null, altar.getBlockPos(), SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                    SoundSource.PLAYERS, 1, 1);
            altarCraft(world, altar, player);
        }
    }

    private void takeInOrCraft(Level world, TileEntityAltar altar, Player playerIn) {
        if (altar.isCanPlaceItem() && altar.handler.getStackInSlot(0).isEmpty()) {
            altar.handler.setStackInSlot(0,
                    ItemHandlerHelper.copyStackWithSize(playerIn.getMainHandItem(), 1));
            if (!playerIn.isCreative()) {
                playerIn.getMainHandItem().shrink(1);
            }
            // 物品展示框同款「放上」音效，1.20.1 新增
            world.playSound(null, altar.getBlockPos(), SoundEvents.ITEM_FRAME_ADD_ITEM,
                    SoundSource.PLAYERS, 1, 1);
            altarCraft(world, altar, playerIn);
        }
    }

    /**
     * 用当前 6 个贡品重算配方，命中就产出。
     */
    private void altarCraft(Level world, TileEntityAltar altar, Player playerIn) {
        final AltarRecipeInventory inv = new AltarRecipeInventory();
        List<BlockPos> posList = altar.getCanPlaceItemPosList().getData();
        for (int i = 0; i < posList.size() && i < AltarRecipeInventory.RECIPES_SIZE; i++) {
            BlockEntity te = world.getBlockEntity(posList.get(i));
            if (te instanceof TileEntityAltar) {
                inv.setItem(i, ((TileEntityAltar) te).getStorageItem());
            }
        }
        if (inv.isEmpty()) {
            return;
        }
        PowerCapability power = IPower.getPower(playerIn);
        world.getRecipeManager().getRecipeFor(InitRecipes.ALTAR_CRAFTING, inv, world)
                .ifPresent(recipe -> spawnResultEntity(world, playerIn, power, recipe, inv, altar));
    }

    private Optional<TileEntityAltar> getAltar(BlockGetter world, BlockPos pos) {
        BlockEntity te = world.getBlockEntity(pos);
        if (te instanceof TileEntityAltar) {
            return Optional.of((TileEntityAltar) te);
        }
        return Optional.empty();
    }

    private void spawnResultEntity(Level world, Player playerIn, PowerCapability power,
                                   AltarRecipe altarRecipe, AltarRecipeInventory inventory,
                                   TileEntityAltar altar) {
        if (power.get() >= altarRecipe.getPowerCost()) {
            power.min(altarRecipe.getPowerCost());
            BlockPos centrePos = getCentrePos(altar.getBlockPosList(), altar.getBlockPos());
            if (world instanceof ServerLevel serverLevel) {
                altarRecipe.spawnOutputEntity(serverLevel, centrePos.above(2), inventory);
            }
            removeAllAltarItem(world, altar);
            spawnParticleInCentre(world, centrePos);
            world.playSound(null, centrePos, InitSounds.ALTAR_CRAFT, SoundSource.VOICE, 1.0f, 1.0f);
            if (playerIn instanceof ServerPlayer serverPlayer) {
                InitTrigger.ALTAR_CRAFT.trigger(serverPlayer, altarRecipe.getId());
            }
        } else if (!world.isClientSide) {
            playerIn.sendMessage(new TranslatableComponent(
                    "message.touhou_little_maid.altar.not_enough_power"), Util.NIL_UUID);
        }
    }

    /**
     * 祭坛中心 = 贡品那一层（y = 点击位置 - 2）所有坐标的平均值。
     *
     * <p>上游这里除以 8，写死成「8 个祭坛格」。这里改成按实际计数求平均，
     * 结构模板改动时不会再算出偏移的中心点；当前模板下两者结果完全相同。</p>
     */
    private BlockPos getCentrePos(PosListData posList, BlockPos posClick) {
        int x = 0;
        int y = posClick.getY() - 2;
        int z = 0;
        int count = 0;
        for (BlockPos pos : posList.getData()) {
            if (pos.getY() == y) {
                x += pos.getX();
                z += pos.getZ();
                count++;
            }
        }
        if (count == 0) {
            return posClick;
        }
        return new BlockPos(x / count, y, z / count);
    }

    private void removeAllAltarItem(Level world, TileEntityAltar altar) {
        for (BlockPos pos : altar.getCanPlaceItemPosList().getData()) {
            this.getAltar(world, pos).ifPresent(te -> {
                te.handler.setStackInSlot(0, ItemStack.EMPTY);
                te.refresh();
                spawnParticleInCentre(world, te.getBlockPos());
            });
        }
    }

    private void spawnParticleInCentre(Level world, BlockPos centrePos) {
        int width = 1;
        int height = 1;
        // 上游用 Forge 暴露的 Block.RANDOM；原版 1.18.2 没有这个字段，
        // 坐标抖动本来就是「这个世界里的随机」，用 world.getRandom() 语义更准。
        // 注意 1.18.2 的 Level#getRandom 返回的还是 java.util.Random（RandomSource 是 1.19 才有的）。
        java.util.Random random = world.getRandom();
        for (int i = 0; i < 5; ++i) {
            double xSpeed = random.nextGaussian() * 0.02;
            double ySpeed = random.nextGaussian() * 0.02;
            double zSpeed = random.nextGaussian() * 0.02;
            world.addParticle(ParticleTypes.CLOUD,
                    centrePos.getX() + random.nextFloat() * width * 2 - width - xSpeed * 10,
                    centrePos.getY() + random.nextFloat() * height - ySpeed * 10,
                    centrePos.getZ() + random.nextFloat() * width * 2 - width - zSpeed * 10,
                    xSpeed, ySpeed, zSpeed);
            world.addParticle(ParticleTypes.SMOKE,
                    centrePos.getX() + random.nextFloat() * width * 2 - width - xSpeed * 10,
                    centrePos.getY() + random.nextFloat() * height - ySpeed * 10,
                    centrePos.getZ() + random.nextFloat() * width * 2 - width - zSpeed * 10,
                    xSpeed, ySpeed, zSpeed);
        }
    }
}
