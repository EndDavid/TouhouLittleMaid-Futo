package com.github.tartaricacid.touhoulittlemaid.entity.passive;

import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.util.TeleportHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;

import javax.annotation.Nullable;

/**
 * 女仆的三段作息坐标：工作点 / 娱乐点 / 睡觉点，外加它们所属的维度。逐行移植自上游
 * {@code entity/passive/SchedulePos}。
 *
 * <h2>它为什么不能只是一个「家」坐标</h2>
 * <p>女仆的日程表会在 {@code WORK} / {@code IDLE} / {@code REST} 三种活动之间切换，
 * 每种活动有自己的活动半径（{@code MaidWorkRange} / {@code MaidIdleRange} /
 * {@code MaidSleepRange}）。所以「居家模式」不是一个点，而是<b>三个点</b>：
 * 早上她围着工作点转、傍晚围着娱乐点转、夜里回睡觉点。上游用一个
 * {@link Activity#getName()} 作键的 {@code KappaCompassMapping} 把三者写进同一份数据。</p>
 *
 * <h2>{@link #configured} 的语义</h2>
 * <p>只有河童的罗盘真正写入过坐标，{@code configured} 才为 {@code true}。
 * 玩家在女仆界面上第一次打开 Home 开关时，走的是
 * {@link #setHomeModeEnable(EntityMaid, BlockPos)}：<b>未配置</b>就把三个点全部设成
 * 她当时站的那一格，<b>已配置</b>就原样沿用罗盘写下的坐标。这就是「先摆好三个点，
 * 再开 Home」能生效、而「先开 Home 再摆点」不会把点冲掉的原因。</p>
 *
 * <h2>{@link #tick} 与 {@link #restrictTo}</h2>
 * <p>{@code restrictTo} 只负责按当前活动把 vanilla 的活动范围（{@code restrictCenter} +
 * {@code restrictRadius}）挪到对应的点上；{@link #tick} 每 40 tick 检查一次，
 * 女仆跑出范围就把寻路目标指回去，跑得太远（超出半径 4 格以上）就直接传送。</p>
 *
 * <h2>与上游的差异</h2>
 * <ul>
 *     <li>上游用 {@code MaidConfig.MAID_WORK_RANGE.get()}；Forge Config API Port 的
 *         {@code ConfigValue#get()} 在配置未加载时会抛异常，本移植版统一走
 *         {@link MaidConfig#workRange()} 这类带默认值的包装方法。</li>
 *     <li>上游 1.18.2 的 {@code EntityMaid} 还有 {@code getSchedulePos()} 之外的
 *         「旧存档 RESTRICT_CENTER 迁移」，本移植版从来没有写过那个键，不需要迁移。</li>
 * </ul>
 */
public final class SchedulePos {
    /**
     * 传送失败时的最大尝试次数，与上游一致。
     *
     * <p>传送会挑周围一格落脚，可能落在方块里 / 悬空 / 岩浆上，所以单次失败很正常，
     * 多试几次能显著提高成功率。</p>
     */
    private static final int MAX_TELEPORT_ATTEMPTS_TIMES = 10;

    private BlockPos workPos;
    private BlockPos idlePos;
    private BlockPos sleepPos;
    private ResourceLocation dimension;
    private boolean configured = false;

    public SchedulePos(BlockPos workPos, BlockPos idlePos, BlockPos sleepPos, ResourceLocation dimension) {
        this.workPos = workPos;
        this.idlePos = idlePos;
        this.sleepPos = sleepPos;
        this.dimension = dimension;
    }

    public SchedulePos(BlockPos workPos, BlockPos idlePos, ResourceLocation dimension) {
        this(workPos, idlePos, idlePos, dimension);
    }

    public SchedulePos(BlockPos workPos, ResourceLocation dimension) {
        this(workPos, workPos, dimension);
    }

    /**
     * 每 tick 调用（服务端）。每 40 tick 收拾一次位置。
     *
     * <p>三个前置判断缺一不可：</p>
     * <ol>
     *     <li>先把活动范围挪到当前活动对应的点上（{@link #restrictTo}）；</li>
     *     <li>已经在范围内就什么都不做 —— 否则会把玩家手动指挥的寻路目标顶掉；</li>
     *     <li>不能动的时候（坐着 / 被抱着 / 骑乘）也不做 —— 传送坐着或正在被抱的女仆
     *         会让她从玩家怀里瞬移出去。</li>
     * </ol>
     */
    public void tick(EntityMaid maid) {
        if (maid.tickCount % 40 == 0) {
            this.restrictTo(maid);
            if (maid.isWithinRestriction()) {
                return;
            }
            if (!maid.canBrainMoving()) {
                return;
            }
            double distanceSqr = maid.getRestrictCenter().distSqr(maid.blockPosition());
            int minTeleportDistance = (int) maid.getRestrictRadius() + 4;
            if (distanceSqr > (minTeleportDistance * minTeleportDistance) && !this.sameWithRestrictCenter(maid)) {
                teleport(maid);
            } else {
                BehaviorUtils.setWalkAndLookTargetMemories(maid, maid.getRestrictCenter(), 0.7f, 3);
            }
        }
    }

    /**
     * 存档。整份数据压在一个子标签 {@code MaidSchedulePos} 里。
     *
     * <p>维度存字符串而不是 id 序号：序号会随维度注册顺序变化，跨存档会指错维度。</p>
     */
    public void save(CompoundTag compound) {
        CompoundTag data = new CompoundTag();
        data.put("Work", NbtUtils.writeBlockPos(this.workPos));
        data.put("Idle", NbtUtils.writeBlockPos(this.idlePos));
        data.put("Sleep", NbtUtils.writeBlockPos(this.sleepPos));
        data.putString("Dimension", this.dimension.toString());
        data.putBoolean("Configured", this.configured);
        compound.put("MaidSchedulePos", data);
    }

    /**
     * 读档。没有 {@code MaidSchedulePos} 子标签时<b>什么都不做</b>：
     * 字段保持构造函数里给的「家 = 当前位置」，这对老存档是正确行为
     * （那些女仆本来就没有罗盘坐标）。
     */
    public void load(CompoundTag compound, EntityMaid maid) {
        if (compound.contains("MaidSchedulePos", Tag.TAG_COMPOUND)) {
            CompoundTag data = compound.getCompound("MaidSchedulePos");
            this.workPos = NbtUtils.readBlockPos(data.getCompound("Work"));
            this.idlePos = NbtUtils.readBlockPos(data.getCompound("Idle"));
            this.sleepPos = NbtUtils.readBlockPos(data.getCompound("Sleep"));
            this.dimension = new ResourceLocation(data.getString("Dimension"));
            this.configured = data.getBoolean("Configured");
            this.restrictTo(maid);
        }
    }

    /**
     * 按<b>当前活动</b>把 vanilla 的活动范围挪到对应的点上。
     *
     * <p>居家模式没开时直接返回：此时活动范围由 {@code MaidConfig.MAID_NON_HOME_RANGE}
     * 那一套控制，不该被作息坐标覆盖。</p>
     */
    public void restrictTo(EntityMaid maid) {
        if (!maid.isHomeModeEnable()) {
            return;
        }
        Activity activity = maid.getScheduleDetail();
        if (activity == Activity.WORK) {
            maid.restrictTo(this.workPos, MaidConfig.workRange());
            return;
        }
        if (activity == Activity.IDLE) {
            maid.restrictTo(this.idlePos, MaidConfig.idleRange());
            return;
        }
        if (activity == Activity.REST) {
            maid.restrictTo(this.sleepPos, MaidConfig.sleepRange());
        }
    }

    public BlockPos getWorkPos() {
        return workPos;
    }

    public void setWorkPos(BlockPos workPos) {
        this.workPos = workPos;
    }

    public BlockPos getIdlePos() {
        return idlePos;
    }

    public void setIdlePos(BlockPos idlePos) {
        this.idlePos = idlePos;
    }

    public BlockPos getSleepPos() {
        return sleepPos;
    }

    public void setSleepPos(BlockPos sleepPos) {
        this.sleepPos = sleepPos;
    }

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public ResourceLocation getDimension() {
        return dimension;
    }

    public void setDimension(ResourceLocation dimension) {
        this.dimension = dimension;
    }

    /**
     * 清空罗盘坐标（上游 {@code ItemKappaCompass} 潜行右击女仆）。
     *
     * <p>注意它<b>不是</b>把三个点设成零：上游把孤立的点并回 {@code workPos}，
     * 并把 {@code configured} 置回 {@code false}，于是下一次开启 Home 会以她当时的
     * 站位的重新锚定。三人成虎地留下「三个点都在 (0,0,0)」会让女仆被限制在世界原点。</p>
     */
    public void clear(EntityMaid maid) {
        this.idlePos = this.workPos;
        this.sleepPos = this.workPos;
        this.configured = false;
        this.dimension = maid.level.dimension().location();
        this.restrictTo(maid);
    }

    /**
     * 开启居家模式时的写入。
     *
     * <p>只在 {@code !configured} 时把三个点设成 {@code pos}；已配置过（玩家用罗盘摆过）
     * 就原样保留，否则每次点 Home 都会把玩家精心设置的三个区域冲成一团。</p>
     */
    public void setHomeModeEnable(EntityMaid maid, BlockPos pos) {
        if (!this.configured) {
            this.workPos = pos;
            this.idlePos = pos;
            this.sleepPos = pos;
            this.dimension = maid.level.dimension().location();
        }
        this.restrictTo(maid);
    }

    /**
     * 三个点里离女仆最近的那个；没配置过返回 {@code null}。
     *
     * <p>用于「打开 Home 时她是不是离自己记录的区域太远」的检查
     * （上游 {@code MaidConfigMessage#handleHome}，超过 32 格就拒绝开启）。</p>
     */
    @Nullable
    public BlockPos getNearestPos(EntityMaid maid) {
        if (this.configured) {
            BlockPos pos = this.workPos;
            double workDistance = maid.blockPosition().distSqr(this.workPos);
            double idleDistance = maid.blockPosition().distSqr(this.idlePos);
            double sleepDistance = maid.blockPosition().distSqr(this.sleepPos);
            if (workDistance > idleDistance) {
                pos = this.idlePos;
                workDistance = idleDistance;
            }
            if (workDistance > sleepDistance) {
                pos = this.sleepPos;
            }
            return pos;
        }
        return null;
    }

    /**
     * 当前的寻路目标是不是已经指向活动范围中心。
     *
     * <p>是的话就不必再传送 —— 她已经「在路上」了，只是还没走到。</p>
     */
    private boolean sameWithRestrictCenter(EntityMaid maid) {
        BlockPos restrictCenter = maid.getRestrictCenter();
        return maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .filter(walkTarget -> walkTarget.getTarget().currentBlockPosition().equals(restrictCenter))
                .isPresent();
    }

    private void teleport(EntityMaid maid) {
        for (int i = 0; i < MAX_TELEPORT_ATTEMPTS_TIMES; ++i) {
            if (TeleportHelper.teleportToRestrictCenter(maid)) {
                return;
            }
        }
    }
}

