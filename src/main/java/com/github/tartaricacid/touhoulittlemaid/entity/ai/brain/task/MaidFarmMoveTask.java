package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IFarmTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidPathFindingBFS;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;

/**
 * 找「可以收获或可以播种」的地块，移植自上游 {@code MaidFarmMoveTask}。
 *
 * <p>扫描前先把背包里的种子拷贝一份到本地列表 —— 扫描循环里会调用几百次
 * {@code canPlant}，每次都去翻物品栏太慢。</p>
 */
public class MaidFarmMoveTask extends MaidMoveToBlockTask {
    private final NonNullList<ItemStack> seeds = NonNullList.create();
    private final IFarmTask task;

    public MaidFarmMoveTask(IFarmTask task, float movementSpeed) {
        super(movementSpeed, 2);
        this.task = task;
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid entityIn, long gameTimeIn) {
        seeds.clear();
        IItemHandler inv = entityIn.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (this.task.isSeed(stack)) {
                seeds.add(stack);
            }
        }
        this.searchForDestination(worldIn, entityIn);
    }

    @Override
    protected boolean shouldMoveTo(ServerLevel worldIn, EntityMaid maid, BlockPos basePos) {
        if (this.task.checkCropPosAbove()) {
            BlockPos above2Pos = basePos.above(2);
            BlockState stateUp2 = worldIn.getBlockState(above2Pos);
            if (!stateUp2.getCollisionShape(worldIn, above2Pos).isEmpty()) {
                return false;
            }
        }

        BlockPos cropPos = basePos.above();
        BlockState cropState = worldIn.getBlockState(cropPos);
        if (this.task.canHarvest(maid, cropPos, cropState)) {
            return true;
        }

        BlockState baseState = worldIn.getBlockState(basePos);
        return this.seeds.stream().anyMatch(seed -> this.task.canPlant(maid, basePos, baseState, seed));
    }

    /**
     * 放宽可达性判定，修掉「西瓜 / 南瓜永远够不着」的问题。
     *
     * <p>{@link MaidPathFindingBFS#canPathReach(BlockPos)} 的语义是
     * 「女仆的<b>脚</b>能否站在 {@code pos} 或 {@code pos.above()} 这一格」。</p>
     * <ul>
     *     <li>小麦 / 胡萝卜这类作物没有碰撞体积，{@code basePos.above()} 就是一个可行走节点，
     *         于是「站在作物所在格」判定成立，农耕正常。</li>
     *     <li>西瓜 / 南瓜是<b>整块实心方块</b>，脚下永远塞不进这一格，
     *         于是 {@code canPathReach} 恒为 {@code false}。
     *         扫描时 {@code shouldMoveTo} 明明返回了 true，却紧接着被可达性判定筛掉，
     *         表现出来就是「女仆对连着瓜梗的瓜完全没有反应」。</li>
     * </ul>
     *
     * <p>但收瓜本来就不需要站进果实里 —— 站在<b>旁边</b>就够
     * （{@code getCloseEnoughDist()} 已经留出 1.5 格容差）。
     * 所以这里退化一层：只要她能够到支撑块四周的任意一格，就算可达。
     * 对无碰撞的作物没有影响（它们本来就可达），对实心果实则是唯一可行的判定方式。</p>
     */
    @Override
    protected boolean checkPathReach(EntityMaid maid, MaidPathFindingBFS pathFinding, BlockPos pos) {
        if (super.checkPathReach(maid, pathFinding, pos)) {
            return true;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (pathFinding.canPathReach(pos.relative(direction))) {
                return true;
            }
        }
        return false;
    }
}
