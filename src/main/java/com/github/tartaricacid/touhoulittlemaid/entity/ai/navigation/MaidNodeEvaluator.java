package com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidSubConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * 女仆专用寻路评估器，移植自上游 {@code entity/ai/navigation/MaidNodeEvaluator}。
 *
 * <p>相对原版只做两件事，且都直接对应「女仆配置界面」里的开关：</p>
 * <ul>
 *     <li><b>能否开门 / 能否开栅栏门</b>：关闭时把 {@link BlockPathTypes#DOOR_WOOD_CLOSED}
 *         降级成 {@link BlockPathTypes#BLOCKED}，女仆就不会规划穿过那扇门的路线，
 *         也不会出现「站在关闭的门前一直撞门」；</li>
 *     <li><b>能否主动爬梯</b>：开启时把梯子所在格及其上下相邻格都当成可走节点，
 *         寻路才会产生竖直方向的路径（原版里梯子对地面生物是不可通行的）。</li>
 * </ul>
 *
 * <p><b>与上游的差异：</b>上游整段重写了 {@code getMaidBlockPathTypeStatic}，
 * 因为它要在 {@code blockState.getBlockPathType(...)} 里拿到 Forge 扩展的路径类型 ——
 * 那个方法只有 Forge 有。这里改为「先让原版算，再按需降级」，
 * 语义等价（原版对关闭的木门与栅栏门都给出 {@code DOOR_WOOD_CLOSED}），
 * 但不必复制两百行原版逻辑，也不会随原版改动而失配。</p>
 */
public class MaidNodeEvaluator extends WalkNodeEvaluator {
    /**
     * 按配置降级「不可开启的门」。
     *
     * <p><b>为什么覆写 {@code getCachedBlockType} 而不是三参的
     * {@code getBlockPathType}：</b>1.18.2 的寻路在算每个节点时走的是
     * {@code getCachedBlockType → getBlockPathType(10 参)} 这一条，
     * 三参重载只在少数直接调用处生效，覆写它不会影响实际寻路。</p>
     */
    @Override
    protected BlockPathTypes getCachedBlockType(Mob mob, int x, int y, int z) {
        BlockPathTypes type = super.getCachedBlockType(mob, x, y, z);
        if (type != BlockPathTypes.DOOR_WOOD_CLOSED || !(mob instanceof EntityMaid maid)) {
            return type;
        }
        BlockState state = this.level.getBlockState(new BlockPos(x, y, z));
        if (state.getBlock() instanceof DoorBlock && !MaidSubConfig.canOpenDoor(maid)) {
            return BlockPathTypes.BLOCKED;
        }
        if (state.getBlock() instanceof FenceGateBlock && !MaidSubConfig.canOpenFenceGate(maid)) {
            return BlockPathTypes.BLOCKED;
        }
        return type;
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z) {
        BlockPathTypes type = super.getBlockPathType(level, x, y, z);
        if (type != BlockPathTypes.DOOR_WOOD_CLOSED || !(this.mob instanceof EntityMaid maid)) {
            return type;
        }
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        if (state.getBlock() instanceof DoorBlock && !MaidSubConfig.canOpenDoor(maid)) {
            return BlockPathTypes.BLOCKED;
        }
        if (state.getBlock() instanceof FenceGateBlock && !MaidSubConfig.canOpenFenceGate(maid)) {
            return BlockPathTypes.BLOCKED;
        }
        return type;
    }

    /**
     * 在原版邻居的基础上，额外把「梯子的上一格 / 下一格」加入候选节点。
     *
     * <p>只加一格就够用：原版 {@code getNeighbors} 本身已经会沿同一列上下扩展，
     * 只要梯子出现在候选里，后续高度就会被自己接上。</p>
     */
    @Override
    public int getNeighbors(Node[] outputArray, Node node) {
        int size = super.getNeighbors(outputArray, node);
        if (!(this.mob instanceof EntityMaid maid) || !MaidSubConfig.isActiveClimbing(maid)) {
            return size;
        }
        size = this.addClimbNode(size, outputArray, node, node.y + 1, maid);
        size = this.addClimbNode(size, outputArray, node, node.y - 1, maid);
        return size;
    }

    private int addClimbNode(int size, Node[] outputArray, Node origin, int y, EntityMaid maid) {
        if (size + 1 >= outputArray.length) {
            return size;
        }
        BlockPos pos = new BlockPos(origin.x, y, origin.z);
        if (!isLadder(this.level, pos, maid)) {
            return size;
        }
        Node node = this.getNode(pos);
        if (node.closed) {
            return size;
        }
        node.costMalus = 0.0F;
        node.type = BlockPathTypes.WALKABLE;
        outputArray[size] = node;
        return size + 1;
    }

    /**
     * 该位置是不是女仆可以攀爬的方块。
     *
     * <p>用原版标签 {@code BlockTags.CLIMBABLE} 判断，而不是硬编码 {@code Blocks.LADDER}：
     * （藤蔓、脚手架、模组梯子）也会被识别，与上游一致。</p>
     */
    public static boolean isLadder(BlockGetter level, BlockPos pos, EntityMaid maid) {
        return level.getBlockState(pos).is(net.minecraft.tags.BlockTags.CLIMBABLE);
    }
}
