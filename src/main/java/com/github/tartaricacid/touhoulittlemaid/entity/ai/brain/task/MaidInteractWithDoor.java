package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidSubConfig;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Objects;

/**
 * 走路时顺手开关门的 CORE 行为，移植自上游 {@code entity/ai/brain/task/MaidInteractWithDoor}。
 *
 * <p>原版 {@code InteractWithDoor} 是给村民与猪灵用的，它只看「能否开门」这一件事。
 * 上游复制一份是为了两件事：</p>
 * <ul>
 *     <li>把「开门」与「开栅栏门」拆成两个独立开关（原版没有栅栏门开关）；</li>
 *     <li>把判定源换成女仆的配置，而不是 {@code Mob.canOpenDoors()}（那是寻路的静态属性，
 *         运行时改不了）。</li>
 * </ul>
 *
 * <p>行为本身保留原版骨架：走过一个节点时打开门 / 栅栏门并记进
 * {@code DOORS_TO_CLOSE}，离开后逐个判断「是否够远 / 是否有同类正在通过」，
 * 满足就关上。这样女仆不会把村庄的门全开着一走了之。</p>
 */
public class MaidInteractWithDoor extends Behavior<EntityMaid> {
    private static final int COOLDOWN_BEFORE_RERUNNING_IN_SAME_NODE = 3;
    private static final double SKIP_CLOSING_DOOR_IF_FURTHER_AWAY_THAN = 8.0D;
    private static final double MAX_DISTANCE_TO_HOLD_DOOR_OPEN_FOR_OTHER_MOBS = 2.0D;

    @Nullable
    private Node lastCheckedNode;
    private int remainingCooldown;

    public MaidInteractWithDoor() {
        super(ImmutableMap.of(MemoryModuleType.PATH, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.DOORS_TO_CLOSE, MemoryStatus.REGISTERED));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        Path path = maid.getBrain().getMemory(MemoryModuleType.PATH).orElse(null);
        if (path == null || path.notStarted() || path.isDone()) {
            return false;
        }
        if (!Objects.equals(this.lastCheckedNode, path.getNextNode())) {
            this.remainingCooldown = COOLDOWN_BEFORE_RERUNNING_IN_SAME_NODE;
            return true;
        }
        if (this.remainingCooldown > 0) {
            --this.remainingCooldown;
        }
        return this.remainingCooldown == 0;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        boolean canOpenDoor = MaidSubConfig.canOpenDoor(maid);
        boolean canOpenFenceGate = MaidSubConfig.canOpenFenceGate(maid);

        Path path = maid.getBrain().getMemory(MemoryModuleType.PATH).orElseThrow();
        this.lastCheckedNode = path.getNextNode();
        Node previousNode = path.getPreviousNode();
        Node nextNode = path.getNextNode();

        if (previousNode != null) {
            BlockPos previousPos = previousNode.asBlockPos();
            this.tryOpen(level, maid, previousPos, canOpenDoor, canOpenFenceGate);
        }
        if (nextNode != null) {
            BlockPos nextPos = nextNode.asBlockPos();
            this.tryOpen(level, maid, nextPos, canOpenDoor, canOpenFenceGate);
        }
        this.closeDoorsThatIHaveOpenedOrPassedThrough(level, maid, previousNode, nextNode);
    }

    /**
     * 该位置若是关闭的木门 / 栅栏门，且对应开关打开，就打开它并登记「稍后关闭」。
     */
    private void tryOpen(ServerLevel level, EntityMaid maid, BlockPos pos,
                         boolean canOpenDoor, boolean canOpenFenceGate) {
        BlockState state = level.getBlockState(pos);
        if (canOpenDoor && state.is(BlockTags.WOODEN_DOORS) && state.getBlock() instanceof DoorBlock door) {
            if (!door.isOpen(state)) {
                door.setOpen(maid, level, state, pos, true);
            }
            this.rememberDoorToClose(level, maid, pos);
            return;
        }
        if (canOpenFenceGate && state.is(BlockTags.FENCE_GATES) && state.getBlock() instanceof FenceGateBlock) {
            if (!state.getValue(FenceGateBlock.OPEN)) {
                setFenceGate(maid, level, state, pos, true);
            }
            this.rememberDoorToClose(level, maid, pos);
        }
    }

    /**
     * 关掉所有「我开过、且已经走远 / 没人在用」的门。
     */
    private void closeDoorsThatIHaveOpenedOrPassedThrough(ServerLevel level, EntityMaid maid,
                                                          @Nullable Node previousNode, @Nullable Node nextNode) {
        Brain<?> brain = maid.getBrain();
        brain.getMemory(MemoryModuleType.DOORS_TO_CLOSE).ifPresent(doors -> {
            Iterator<GlobalPos> iterator = doors.iterator();
            while (iterator.hasNext()) {
                GlobalPos globalPos = iterator.next();
                BlockPos pos = globalPos.pos();
                boolean stillOnPath = (previousNode != null && previousNode.asBlockPos().equals(pos)
                                       && (nextNode == null || !maid.blockPosition().equals(nextNode.asBlockPos())))
                                      || (nextNode != null && nextNode.asBlockPos().equals(pos));
                if (stillOnPath) {
                    continue;
                }
                if (isTooFarAway(level, maid, globalPos)) {
                    iterator.remove();
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (state.is(BlockTags.WOODEN_DOORS) && state.getBlock() instanceof DoorBlock door) {
                    if (!door.isOpen(state) || isOtherMaidComingThroughDoor(level, maid, pos)) {
                        iterator.remove();
                    } else {
                        door.setOpen(maid, level, state, pos, false);
                        iterator.remove();
                    }
                } else if (state.is(BlockTags.FENCE_GATES) && state.getBlock() instanceof FenceGateBlock) {
                    if (!state.getValue(FenceGateBlock.OPEN) || isOtherMaidComingThroughDoor(level, maid, pos)) {
                        iterator.remove();
                    } else {
                        setFenceGate(maid, level, state, pos, false);
                    }
                } else {
                    iterator.remove();
                }
            }
        });
    }

    /**
     * 是否有其它女仆正要穿过这扇门。
     *
     * <p>只看女仆自己（同样是 {@code EntityMaid}）：如果一只女仆跟在另一只后面，
     * 前一只不该刚过去就把门关上，否则后面那只又得重新开门。</p>
     */
    private static boolean isOtherMaidComingThroughDoor(ServerLevel level, EntityMaid maid, BlockPos pos) {
        return maid.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES)
                .map(list -> list.stream()
                        .filter(entity -> entity != maid && entity instanceof EntityMaid)
                        .filter(entity -> pos.closerToCenterThan(entity.position(),
                                MAX_DISTANCE_TO_HOLD_DOOR_OPEN_FOR_OTHER_MOBS))
                        .anyMatch(entity -> isMaidComingThroughDoor((EntityMaid) entity, pos)))
                .orElse(false);
    }

    private static boolean isMaidComingThroughDoor(EntityMaid maid, BlockPos pos) {
        return maid.getBrain().getMemory(MemoryModuleType.PATH)
                .filter(path -> !path.isDone())
                .map(path -> {
                    Node previousNode = path.getPreviousNode();
                    Node nextNode = path.getNextNode();
                    return (previousNode != null && pos.equals(previousNode.asBlockPos()))
                           || (nextNode != null && pos.equals(nextNode.asBlockPos()));
                })
                .orElse(false);
    }

    private static boolean isTooFarAway(ServerLevel level, EntityMaid maid, GlobalPos globalPos) {
        return globalPos.dimension() != level.dimension()
               || !globalPos.pos().closerToCenterThan(maid.position(), SKIP_CLOSING_DOOR_IF_FURTHER_AWAY_THAN);
    }

    private void rememberDoorToClose(ServerLevel level, EntityMaid maid, BlockPos pos) {
        Brain<?> brain = maid.getBrain();
        GlobalPos globalPos = GlobalPos.of(level.dimension(), pos);
        brain.getMemory(MemoryModuleType.DOORS_TO_CLOSE).ifPresentOrElse(
                doors -> doors.add(globalPos),
                () -> brain.setMemory(MemoryModuleType.DOORS_TO_CLOSE, Sets.newHashSet(globalPos)));
    }

    /**
     * 栅栏门没有 {@code DoorBlock#setOpen} 那样的便捷方法，只能手动改状态 + 播声音 + 发事件。
     */
    private static void setFenceGate(@Nullable Entity entity, Level level, BlockState state,
                                     BlockPos pos, boolean open) {
        level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, open),
                Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
        level.levelEvent(null, open ? LevelEvent.SOUND_OPEN_FENCE_GATE : LevelEvent.SOUND_CLOSE_FENCE_GATE, pos, 0);
        level.gameEvent(entity, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }
}

