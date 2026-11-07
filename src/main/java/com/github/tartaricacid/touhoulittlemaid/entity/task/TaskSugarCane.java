package com.github.tartaricacid.touhoulittlemaid.entity.task;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.task.IFarmTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * 甘蔗模式，移植自上游 {@code entity/task/TaskSugarCane}。
 *
 * <p>只收「第三段」—— 也就是最上面那一段：下方两格都还是甘蔗、且第 3 格下方能托住
 * 甘蔗（泥土 / 沙子）。这样收完还剩两段继续长，与原版手感一致。</p>
 */
public class TaskSugarCane implements IFarmTask {
    private static final ResourceLocation UID = new ResourceLocation(TouhouLittleMaid.MOD_ID, "sugar_cane");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.SUGAR_CANE.getDefaultInstance();
    }

    @Override
    public boolean isSeed(ItemStack stack) {
        return stack.getItem() == Items.SUGAR_CANE;
    }

    @Override
    public boolean canHarvest(EntityMaid maid, BlockPos cropPos, BlockState cropState) {
        BlockState blockDownState = maid.level.getBlockState(cropPos.below());
        BlockState blockDown2State = maid.level.getBlockState(cropPos.below(2));
        return cropState.is(Blocks.SUGAR_CANE) && blockDownState.is(Blocks.SUGAR_CANE)
               && this.canSustainSugarCane(blockDown2State);
    }

    @Override
    public void harvest(EntityMaid maid, BlockPos cropPos, BlockState cropState) {
        maid.destroyBlock(cropPos);
    }

    @Override
    public boolean canPlant(EntityMaid maid, BlockPos basePos, BlockState baseState, ItemStack seed) {
        Level world = maid.level;
        BlockPos cropPos = basePos.above();
        BlockState aboveState = world.getBlockState(cropPos);
        if (!aboveState.getMaterial().isReplaceable() || aboveState.getMaterial().isLiquid()) {
            return false;
        }
        return this.canSustainSugarCane(baseState) && this.hasWaterSourceBlock(maid.level, basePos);
    }

    @Override
    public ItemStack plant(EntityMaid maid, BlockPos basePos, BlockState baseState, ItemStack seed) {
        if (seed.getItem() == Items.SUGAR_CANE) {
            maid.placeItemBlock(basePos.above(), seed);
        }
        return seed;
    }

    @Override
    public double getCloseEnoughDist() {
        return 2.0;
    }

    private boolean canSustainSugarCane(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND);
    }

    private boolean hasWaterSourceBlock(Level world, BlockPos basePos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos offsetPos = basePos.relative(direction);
            BlockState offsetState = world.getBlockState(offsetPos);
            FluidState fluidState = world.getFluidState(offsetPos);
            if (fluidState.is(FluidTags.WATER) || offsetState.is(Blocks.FROSTED_ICE)) {
                return true;
            }
        }
        return false;
    }
}

