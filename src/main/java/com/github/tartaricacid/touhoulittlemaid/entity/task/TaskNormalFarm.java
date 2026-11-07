package com.github.tartaricacid.touhoulittlemaid.entity.task;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.task.IFarmTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

/**
 * 普通作物农耕模式（小麦 / 胡萝卜 / 土豆 / 甜菜 / 下界疣 / 瓜藤），
 * 移植自上游 {@code entity/task/TaskNormalFarm}。
 *
 * <p>与上游的差异都来自 Forge → 原版的接口替换，判定结果等价：</p>
 * <ul>
 *     <li>{@code IPlantable#getPlantType(...) == PlantType.CROP}（判定「这是作物种子」）
 *         → 原版的 {@link CropBlock} / {@link StemBlock}。Forge 的 {@code PlantType.CROP}
 *         恰好覆盖这两类方块，甜菜根 / 小麦 / 胡萝卜 / 土豆是 {@code CropBlock}，
 *         西瓜 / 南瓜种子是 {@code StemBlock}。</li>
 *     <li>{@code BlockState#canSustainPlant(...)}（判定「这块地能种」）
 *         → {@code Blocks.FARMLAND}。原版 {@code CropBlock#mayPlaceOn} 与
 *         {@code StemBlock#mayPlaceOn} 都只认耕地，判定完全一致。</li>
 *     <li>{@code CropBlockAccessor}（mixin 取出 age 属性）
 *         → 1.18.2 的 {@link CropBlock#getAgeProperty()} <b>本来就是 public</b>，
 *         不需要 mixin。</li>
 * </ul>
 */
public class TaskNormalFarm implements IFarmTask {
    private static final ResourceLocation NAME = new ResourceLocation(TouhouLittleMaid.MOD_ID, "farm");

    @Override
    public ResourceLocation getUid() {
        return NAME;
    }

    @Override
    public ItemStack getIcon() {
        return Items.IRON_HOE.getDefaultInstance();
    }

    @Override
    public boolean isSeed(ItemStack stack) {
        if (stack.getItem() == Items.NETHER_WART) {
            return true;
        }
        return this.isCropSeed(stack);
    }

    private boolean isCropSeed(ItemStack stack) {
        if (stack.getItem() instanceof ItemNameBlockItem blockNamedItem) {
            Block block = blockNamedItem.getBlock();
            return block instanceof CropBlock || block instanceof StemBlock;
        }
        return false;
    }

    @Override
    public boolean canHarvest(EntityMaid maid, BlockPos cropPos, BlockState cropState) {
        Block block = cropState.getBlock();
        if (block instanceof CropBlock crop && crop.isMaxAge(cropState)) {
            return true;
        }
        return block == Blocks.NETHER_WART && cropState.getValue(NetherWartBlock.AGE) >= 3;
    }

    @Override
    public void harvest(EntityMaid maid, BlockPos cropPos, BlockState cropState) {
        if (maid.getMainHandItem().getItem() instanceof HoeItem) {
            // 拿锄头时整块打掉（上游同款：省时间，但作物要重种）
            maid.destroyBlock(cropPos);
            return;
        }

        CombinedInvWrapper availableInv = maid.getAvailableInv(false);
        Block cropBlock = cropState.getBlock();
        maid.level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, cropPos, Block.getId(cropState));

        if (cropBlock instanceof CropBlock crop) {
            BlockEntity blockEntity = cropState.hasBlockEntity() ? maid.level.getBlockEntity(cropPos) : null;
            maid.dropResourcesToMaidInv(cropState, maid.level, cropPos, blockEntity, maid, maid.getMainHandItem());
            // 直接把 Age 归零，作物继续生长（不拔根）
            IntegerProperty ageProperty = crop.getAgeProperty();
            if (cropState.hasProperty(ageProperty)) {
                try {
                    cropState = cropState.setValue(ageProperty, 0);
                } catch (IllegalArgumentException ignore) {
                    // 极少数模组作物的年龄区间不含 0，忽略即可
                }
            }
            maid.level.setBlock(cropPos, cropState, Block.UPDATE_ALL);
            maid.level.gameEvent(maid, GameEvent.BLOCK_CHANGE, cropPos);
            return;
        }

        if (cropBlock == Blocks.NETHER_WART) {
            ItemStack dropItemStack = new ItemStack(Items.NETHER_WART);
            ItemStack remindItemStack = ItemHandlerHelper.insertItemStacked(availableInv, dropItemStack, false);
            if (!remindItemStack.isEmpty()) {
                Block.popResource(maid.level, cropPos, remindItemStack);
            }
            maid.level.setBlock(cropPos, Blocks.NETHER_WART.defaultBlockState(), Block.UPDATE_ALL);
            maid.level.gameEvent(maid, GameEvent.BLOCK_CHANGE, cropPos);
        }
    }

    @Override
    public boolean canPlant(EntityMaid maid, BlockPos basePos, BlockState baseState, ItemStack seed) {
        BlockState aboveState = maid.level.getBlockState(basePos.above());
        if (!aboveState.getMaterial().isReplaceable() || aboveState.getMaterial().isLiquid()) {
            return false;
        }
        if (!this.isCropSeed(seed)) {
            return false;
        }
        return baseState.is(Blocks.FARMLAND);
    }

    @Override
    public ItemStack plant(EntityMaid maid, BlockPos basePos, BlockState baseState, ItemStack seed) {
        if (this.isCropSeed(seed)) {
            maid.placeItemBlock(basePos.above(), seed);
        }
        return seed;
    }
}

