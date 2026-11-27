package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.MaidEdibleBlockAction;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.MaidEdibleBlockManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidPathFindingBFS;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 「偷吃方块食物」的寻路任务，移植自上游同名类。
 *
 * <h2>两种模式写在同一个记忆里</h2>
 * <p>女仆要么去吃<b>世界里已有的</b>方块食物（{@link MaidEdibleBlockAction#TRY_STEAL}），
 * 要么把<b>背包里的</b>食物摆到零食架上再吃（{@link MaidEdibleBlockAction#TRY_PLACE}）。
 * 选择逻辑在 {@link #start}：背包里能摆的优先（因为那是自己的，不算偷），
 * 否则去偷。选完写进 {@code MAID_EDIBLE_BLOCK_ACTION} 记忆，交给
 * {@link MaidStealEdibleUseTask} 执行。</p>
 *
 * <h2>和普通「走到方块」任务的三处差异</h2>
 * <ul>
 *     <li><b>搜索半径小</b>：只搜 5 格。偷吃本来就是顺手的事，不该让她跑半张地图去啃派；</li>
 *     <li><b>冷却长</b>：找到一次之后 45 秒内不再尝试，避免「一直吃一直爽」；</li>
 *     <li><b>可达性放宽</b>：检查目标周围 3×3×3 里有没有任何一个点能站人。
 *         派/盛宴是多方块或带碰撞箱的方块，严格判定经常「够不着」自己。</li>
 * </ul>
 *
 * <p><b>调试开关</b>：{@link #DEBUG} 置 {@code true} 时跳过冷却与概率检查，
 * 方便在开发环境里立刻复现。上游用 {@code /tlm debug} 之类切换，本移植版没有那条命令，
 * 需要时直接改这个字段（它只影响读取，不写盘）。</p>
 */
public class MaidStealEdibleMoveBlockTask extends MaidMoveToBlockTask {
    /**
     * 水平搜索半径（比基类的 {@code getRestrictRadius} 更小）。
     */
    private static final int HORIZONTAL_SEARCH_RANGE = 5;
    /**
     * 找到一次之后的冷却（45 秒）。
     */
    private static final int NEXT_CHECK_TICK_COUNT = 45 * 20;
    /**
     * 判定可达性时额外检查的立方范围。
     */
    private static final BoundingBox CHECK_RANGE = new BoundingBox(-1, -1, -1, 1, 1, 1);

    @VisibleForDebug
    public static boolean DEBUG = false;

    private final MemoryModuleType<MaidEdibleBlockAction> action;

    /**
     * 当前打算摆放的物品栈（{@code TRY_PLACE} 模式下才有值）。
     */
    @Nullable
    private ItemStack placedStack;

    public MaidStealEdibleMoveBlockTask(float movementSpeed) {
        super(movementSpeed, 2);
        this.setMaxCheckRate(NEXT_CHECK_TICK_COUNT);
        this.action = InitEntities.MAID_EDIBLE_BLOCK_ACTION;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid owner) {
        if (DEBUG) {
            return true;
        }
        return super.checkExtraStartConditions(worldIn, owner);
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        Optional<MaidEdibleBlockAction> memory = maid.getBrain().getMemory(this.action);
        if (memory.isPresent() && memory.get() == MaidEdibleBlockAction.TRY_STEAL) {
            // 背包里有能摆成方块食物的东西吗？有就改走「摆放」分支。
            if (this.findPlacableInInventory(maid)) {
                return;
            }
            // 没有就老老实实偷吃。
            maid.getBrain().setMemory(this.action, MaidEdibleBlockAction.TRY_STEAL);
        } else {
            maid.getBrain().setMemory(this.action, MaidEdibleBlockAction.TRY_STEAL);
        }
        this.searchForDestination(worldIn, maid);
    }

    /**
     * 在背包里找一件能摆成方块食物的物品。
     *
     * @return {@code true} 表示已切到 {@code TRY_PLACE} 并完成了搜索
     */
    private boolean findPlacableInInventory(EntityMaid maid) {
        CombinedInvWrapper inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            for (IMaidEdibleBlock edibleBlock : MaidEdibleBlockManager.getEdibleBlocks()) {
                if (edibleBlock.canPlaceAsFood(maid, stack, i)) {
                    // 记住物品栈：UseTask 里要用同一份去摆放
                    this.placedStack = stack;
                    maid.getBrain().setMemory(this.action, MaidEdibleBlockAction.TRY_PLACE);
                    // searchForDestination 需要 ServerLevel，这里从 maid.level 取
                    if (maid.level instanceof ServerLevel serverLevel) {
                        this.searchForDestination(serverLevel, maid);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean shouldMoveTo(ServerLevel worldIn, EntityMaid maid, BlockPos pos) {
        BlockState blockState = worldIn.getBlockState(pos);
        MaidEdibleBlockAction memory = maid.getBrain().getMemory(this.action)
                .orElse(MaidEdibleBlockAction.TRY_STEAL);
        for (IMaidEdibleBlock edibleBlock : MaidEdibleBlockManager.getEdibleBlocks()) {
            if (memory == MaidEdibleBlockAction.TRY_PLACE) {
                if (this.placedStack == null) {
                    return false;
                }
                if (edibleBlock.shouldPlaceTo(maid, pos, blockState, this.placedStack)) {
                    return true;
                }
            } else if (edibleBlock.shouldMoveTo(maid, pos, blockState)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 放宽可达性：目标周围 3×3×3 里任何一个点能站就算可达。
     */
    @Override
    protected boolean checkPathReach(EntityMaid maid, MaidPathFindingBFS pathFinding, BlockPos pos) {
        for (int x = CHECK_RANGE.minX(); x <= CHECK_RANGE.maxX(); x++) {
            for (int y = CHECK_RANGE.minY(); y <= CHECK_RANGE.maxY(); y++) {
                for (int z = CHECK_RANGE.minZ(); z <= CHECK_RANGE.maxZ(); z++) {
                    if (pathFinding.canPathReach(pos.offset(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected int getHorizontalSearchRange(EntityMaid maid) {
        return Math.min(super.getHorizontalSearchRange(maid), HORIZONTAL_SEARCH_RANGE);
    }

    @Override
    protected void setNextCheckTickCount(int nextCheckTickCount) {
        // 无论基类想设成多少，都强制用 45 秒 —— 偷吃不值得每 5 tick 试一次
        super.setNextCheckTickCount(NEXT_CHECK_TICK_COUNT);
    }
}

