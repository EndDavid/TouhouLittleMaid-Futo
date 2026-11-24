package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.data.AttackListData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidSubConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.PickType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.TabIndex;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.network.chat.TranslatableComponent;

/**
 * 客户端到服务端的「女仆设置」同步包（common 侧）。
 *
 * <p><b>为什么必须有它：</b>{@code homeModeEnable} / {@code pickup} / 当前工作模式
 * 都是 {@code SynchedEntityData}，服务端每次数据变更都会把值同步给客户端。
 * 如果客户端直接 {@code maid.setPickup(...)}，下一次同步就会把它改回去 ——
 * 表现是「点了按钮没反应，或闪一下弹回」。所以客户端只发意图，服务端负责写入。</p>
 *
 * <p><b>载荷格式：</b>第一个字节永远是动作码，紧接着永远是实体 id；之后按动作码决定
 * 后续字段。读取必须在网络线程里、{@code server.execute} 之前完成，
 * 因为缓冲区出了回调就被释放了。</p>
 *
 * <p>发送端见 {@code client.screen.MaidClientPacketSender}（那里才会引用
 * {@code ClientPlayNetworking}，避免专用服务端去加载客户端类）。</p>
 *
 * <p>上游对应的是四个各自独立的包：{@code MaidConfigMessage}（家/拾物/骑乘/日程）、
 * {@code MaidSubConfigMessage}（女仆配置界面那 8 项）、{@code MaidTaskMessage}（工作模式）、
 * {@code ToggleTabMessage}（页签切换），外加 {@code SetAttackListMessage}。
 * 这里用一个通道 + 动作码区分，语义一致，只是少了样板代码。</p>
 */
public final class MaidConfigPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "maid_config");

    /**
     * 居家模式。
     */
    public static final byte HOME = 0;
    /**
     * 拾取开关。
     */
    public static final byte PICKUP = 1;
    /**
     * 切换工作模式（附带一个 ResourceLocation）。
     */
    public static final byte TASK = 2;
    /**
     * 切换页签（附带一个 int），服务端据此关掉当前菜单、打开另一个。
     */
    public static final byte TAB = 3;
    /**
     * 女仆配置界面：显示背包。
     */
    public static final byte SHOW_BACKPACK = 4;
    /**
     * 女仆配置界面：显示背部物品。
     */
    public static final byte SHOW_BACK_ITEM = 5;
    /**
     * 女仆配置界面：显示聊天气泡。
     */
    public static final byte SHOW_CHAT_BUBBLE = 6;
    /**
     * 女仆配置界面：能否开门。
     */
    public static final byte OPEN_DOOR = 7;
    /**
     * 女仆配置界面：能否开栅栏门。
     */
    public static final byte OPEN_FENCE_GATE = 8;
    /**
     * 女仆配置界面：能否主动爬梯。
     */
    public static final byte ACTIVE_CLIMBING = 9;
    /**
     * 女仆配置界面：语音频率（附带一个 float，0.0 ~ 1.0）。
     */
    public static final byte SOUND_FREQ = 10;
    /**
     * 女仆配置界面：拾取类型（附带一个字符串 id）。
     */
    public static final byte PICK_TYPE = 11;
    /**
     * 任务设置界面：提交整份攻击名单（关闭界面时发一次）。
     */
    public static final byte ATTACK_LIST = 12;
    /**
     * 命名牌界面：提交新的名字（附带一个字符串 + 一个「总是显示名字」的布尔值）。
     */
    public static final byte NAME_TAG = 13;
    /**
     * 主界面：「能否被骑乘 / 抱起」开关。
     *
     * <p>上游这是个根字段（{@code MaidConfigManager.dataRideable}），不在女仆配置界面里，
     * 由主界面左下角那个「抱」按钮切换 —— 所以动作码挂在布尔类分支上，
     * 与 {@link #HOME} / {@link #PICKUP} 同一套处理。</p>
     */
    public static final byte RIDEABLE = 14;

    /**
     * 更换皮肤 / 模型（附带模型 id 字符串）。
     *
     * <p>对应上游 {@code MaidModelMessage}。{@code ModelId} 是
     * {@code SynchedEntityData}，客户端直接写会被服务端下一次同步覆盖，
     * 所以必须走这个包，由服务端校验归属后再写。
     */
    public static final byte MODEL = 15;

    /**
     * 切换日程表（白班 / 夜班 / 全天）。
     *
     * <p>上游把它塞在 {@code MaidConfigMessage} 里，与 home / pick / ride 一起发；
     * 本移植版每个开关各占一个动作码，所以单开一个。
     * 载荷是一个枚举 ordinal（见 {@link MaidSchedule}），不是布尔。</p>
     */
    public static final byte SCHEDULE = 16;

    /**
     * 开启居家模式时，女仆距离她记录的作息点允许的最大距离（格）。
     *
     * <p>与上游 {@code MaidConfigMessage#handleHome} 里的 {@code 32 * 32} 一致。
     * 超过这个值就拒绝开启，因为 {@code SchedulePos#tick} 会立刻把她传送过去，
     * 而玩家点开关时完全没预期到会瞬移。</p>
     */
    private static final int MAX_HOME_DISTANCE = 32;

    private MaidConfigPacket() {
    }

    /**
     * 服务端注册接收器（在主入口调用）。
     */
    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    // 先读完整个缓冲区，再交给主线程 —— 顺序不能颠倒
                    byte action = buf.readByte();
                    int entityId = buf.readInt();
                    switch (action) {
                        case TASK -> {
                            ResourceLocation taskUid = buf.readResourceLocation();
                            server.execute(() -> handleTask(player, taskUid, entityId));
                        }
                        case TAB -> {
                            int tab = buf.readInt();
                            server.execute(() -> handleTab(player, tab, entityId));
                        }
                        case SOUND_FREQ -> {
                            float value = buf.readFloat();
                            server.execute(() -> handleSoundFreq(player, value, entityId));
                        }
                        case PICK_TYPE -> {
                            String id = buf.readUtf(64);
                            server.execute(() -> handlePickType(player, id, entityId));
                        }
                        case ATTACK_LIST -> {
                            AttackListData data = AttackListData.readFromBuf(buf);
                            server.execute(() -> handleAttackList(player, data, entityId));
                        }
                        case NAME_TAG -> {
                            String name = buf.readUtf(32);
                            boolean alwaysShow = buf.readBoolean();
                            server.execute(() -> handleNameTag(player, name, alwaysShow, entityId));
                        }
                        case MODEL -> {
                            String modelId = buf.readUtf(128);
                            server.execute(() -> handleModel(player, modelId, entityId));
                        }
                        case RIDEABLE -> {
                            boolean value = buf.readBoolean();
                            server.execute(() -> handleRideable(player, value, entityId));
                        }
                        case SCHEDULE -> {
                            MaidSchedule schedule = buf.readEnum(MaidSchedule.class);
                            server.execute(() -> handleSchedule(player, schedule, entityId));
                        }
                        default -> {
                            boolean value = buf.readBoolean();
                            server.execute(() -> handle(player, action, value, entityId));
                        }
                    }
                });
    }

    /**
     * 布尔类动作的统一入口。
     */
    private static void handle(ServerPlayer player, byte action, boolean value, int entityId) {
        EntityMaid maid = findOwnedMaid(player, entityId);
        if (maid == null) {
            return;
        }
        switch (action) {
            case HOME -> applyHome(player, maid, value);
            case PICKUP -> maid.setPickup(value);
            case SHOW_BACKPACK -> MaidSubConfig.setShowBackpack(maid, value);
            case SHOW_BACK_ITEM -> MaidSubConfig.setShowBackItem(maid, value);
            case SHOW_CHAT_BUBBLE -> MaidSubConfig.setShowChatBubble(maid, value);
            case OPEN_DOOR -> MaidSubConfig.setOpenDoor(maid, value);
            case OPEN_FENCE_GATE -> MaidSubConfig.setOpenFenceGate(maid, value);
            case ACTIVE_CLIMBING -> MaidSubConfig.setActiveClimbing(maid, value);
            default -> {
            }
        }
    }

    /**
     * 页签切换。
     *
     * <p>服务端<b>复核</b> tab 的取值：客户端可以发任意整数，
     * 直接透传等于把「打开哪个菜单」的决定权交给了客户端。</p>
     */
    private static void handleTab(ServerPlayer player, int tab, int entityId) {
        EntityMaid maid = findOwnedMaid(player, entityId);
        if (maid == null) {
            return;
        }
        if (tab != TabIndex.MAIN && tab != TabIndex.TASK_CONFIG && tab != TabIndex.MAID_CONFIG) {
            return;
        }
        maid.openMaidGui(player, tab);
    }

    private static void handleSoundFreq(ServerPlayer player, float value, int entityId) {
        EntityMaid maid = findOwnedMaid(player, entityId);
        if (maid != null) {
            MaidSubConfig.setSoundFreq(maid, value);
        }
    }

    private static void handlePickType(ServerPlayer player, String id, int entityId) {
        EntityMaid maid = findOwnedMaid(player, entityId);
        PickType type = PickType.byId(id);
        if (maid != null && type != null) {
            maid.setPickupType(type);
        }
    }

    /**
     * 提交攻击名单。
     *
     * <p>必须重建行为树：攻击判定是在行为树里读名单的，
     * 不重建的话新名单要等下一次 Brain 自然重置才生效（可能是几分钟后）。</p>
     */
    private static void handleAttackList(ServerPlayer player, AttackListData data, int entityId) {
        EntityMaid maid = findOwnedMaid(player, entityId);
        if (maid == null) {
            return;
        }
        maid.setAttackList(data);
        if (maid.level instanceof ServerLevel serverLevel) {
            maid.refreshBrain(serverLevel);
        }
    }

    /**
     * 给女仆改名（上游 {@code SendNameTagMessage}）。
     *
     * <p>三道服务端复核，缺一不可 —— 这个包是玩家可控的：</p>
     * <ol>
     *     <li>女仆必须属于这个玩家（{@link #findOwnedMaid}）；</li>
     *     <li>主手必须<b>还拿着</b>命名牌（防止「改完名再发一次包」白嫖改名）；</li>
     *     <li>名字截断到 32 字符（上游同值），空名字直接丢弃。</li>
     * </ol>
     *
     * <p>成功后与上游一致：设名字、按玩家选择决定是否常显、`setPersistenceRequired`
     * （改过名的女仆不再被当成可自然清除的生物），非创造模式消耗一张命名牌。</p>
     */
    private static void handleNameTag(ServerPlayer player, String name, boolean alwaysShow, int entityId) {
        EntityMaid maid = findOwnedMaid(player, entityId);
        if (maid == null) {
            return;
        }
        String trimmed = name.length() > 32 ? name.substring(0, 32) : name;
        if (trimmed.trim().isEmpty()) {
            return;
        }
        if (!player.getMainHandItem().is(net.minecraft.world.item.Items.NAME_TAG)) {
            return;
        }
        maid.setCustomName(new net.minecraft.network.chat.TextComponent(trimmed));
        maid.setCustomNameVisible(alwaysShow);
        maid.setPersistenceRequired();
        if (!player.isCreative()) {
            player.getMainHandItem().shrink(1);
        }
    }

    /**
     * 按实体 id 找到「该玩家拥有」的女仆；找不到或不属于该玩家时返回 {@code null}。
     *
     * <p>所有动作都要过这一关：网络包是玩家可控的，不能只信客户端。</p>
     */
    private static EntityMaid findOwnedMaid(ServerPlayer player, int entityId) {
        if (player.level.getEntity(entityId) instanceof EntityMaid maid && maid.isOwnedByPlayer(player)) {
            return maid;
        }
        return null;
    }

    /**
     * 切换工作模式。
     *
     * <p>服务端也要复核 {@code isEnable}：客户端那边的任务按钮虽然会吞掉禁用按钮的
     * 点击，但网络包是玩家可控的，不能只信客户端。</p>
     */
    private static void handleTask(ServerPlayer player, ResourceLocation taskUid, int entityId) {
        EntityMaid maid = findOwnedMaid(player, entityId);
        if (maid == null) {
            return;
        }
        TaskManager.findTask(taskUid)
                .filter(task -> task.isEnable(maid))
                .ifPresent(task -> {
                    maid.setTask(task);
                    // 「安排工作」进度（上游 MaidTaskMessage 在写入成功后打判据）
                    InitTrigger.MAID_EVENT.trigger(player, TriggerType.SWITCH_TASK);
                });
    }

    /**
     * 切换居家模式（上游 {@code MaidConfigMessage#handleHome}）。
     *
     * <p>这是「开了居家模式后女仆站着不动」的根因：{@code hasRestriction()} 返回
     * {@code isHomeModeEnable()}，一旦为 {@code true}，所有「是否在工作范围内」的判定
     * （{@code isWithinRestriction} / {@code searchDimension} / 寻路目标点）都会生效。
     * 而活动范围中心默认是 {@code BlockPos.ZERO}（世界原点 y=0），女仆实际可能站在 y=-60，
     * 距离远超半径 —— 于是她认为自己「一直在界外」，既不走路也不干活。</p>
     *
     * <h2>两个拒绝开启的条件</h2>
     * <p>上游在写入坐标<b>之前</b>会先检查，本移植版逐条照搬：</p>
     * <ol>
     *     <li><b>维度不一致</b>：罗盘记录的是主世界的坐标，女仆却在下界。此时的坐标
     *         对她毫无意义，开了 Home 会被 {@code SchedulePos#tick} 传送到
     *         「同一个数字、不同的世界」里去；</li>
     *     <li><b>距离超过 32 格</b>：同样会被 tick 传送 —— 玩家刚点下开关，人就消失了，
     *         而且往往是从自己家里被传送到几十上百格之外。</li>
     * </ol>
     * <p>两种情况下都用 {@link CheckSchedulePosPacket} 弹一条说明。之所以不用聊天栏：
     * 玩家此刻正开着女仆界面，聊天栏被挡住看不见。</p>
     *
     * <p><b>为什么不在这里顺手关掉 Home：</b>上游先 {@code return}，
     * {@code setHomeModeEnable} 根本不会执行，所以开关保持原样（客户端会把它画回未开启），
     * 这正是我们要的 —— 拒绝就是拒绝，不能「悄悄改成另一个状态」。</p>
     */
    private static void applyHome(ServerPlayer player, EntityMaid maid, boolean enable) {
        if (enable) {
            ResourceLocation dimension = maid.getSchedulePos().getDimension();
            if (!dimension.equals(maid.level.dimension().location())) {
                CheckSchedulePosPacket.send(player, new TranslatableComponent(
                        "message.touhou_little_maid.kappa_compass.maid_dimension_check"));
                return;
            }
            BlockPos nearestPos = maid.getSchedulePos().getNearestPos(maid);
            if (nearestPos != null
                    && nearestPos.distSqr(maid.blockPosition()) > MAX_HOME_DISTANCE * MAX_HOME_DISTANCE) {
                CheckSchedulePosPacket.send(player, new TranslatableComponent(
                        "message.touhou_little_maid.check_schedule_pos.too_far"));
                return;
            }
            // 未配置过（没用罗盘摆过点）就三个点全部锚在这里；
            // 已配置过就沿用罗盘写下的坐标（见 SchedulePos#setHomeModeEnable）
            maid.getSchedulePos().setHomeModeEnable(maid, maid.blockPosition());
        } else {
            // 上游同款：关掉时把活动半径还原成非居家半径，并把中心归零。
            // 此时 hasRestriction() 为 false，中心值只是个占位符。
            maid.restrictTo(BlockPos.ZERO, MaidConfig.nonHomeRange());
            // 关掉居家后清掉寻路目标，否则她还会朝旧的中心点走一段
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        maid.setHomeModeEnable(enable);
    }

    /**
     * 「能否被骑乘」开关（上游 {@code MaidConfigMessage} 里的 {@code ride} 字段）。
     *
     * <p><b>关掉时要顺手把她放下来。</b>上游的处理是：</p>
     * <ul>
     *     <li>1.18.2：<b>任何</b>设置变更都会 {@code maid.stopRiding()}（那时 home / pickup /
     *         ride / schedule 挤在同一个包里，所以每个按钮都会顺带落地）；</li>
     *     <li>1.20.1：只在 {@code ride} 真的从 true 变成 false 时才放下来，并且跳过
     *         「娱乐方块」（{@code EntitySit}）与飞行中的扫帚 —— 前者是设计如此，
     *         后者在半空脱离会掉下来。</li>
     * </ul>
     *
     * <p>本移植版对齐 1.20.1：只在「关掉」时放人。{@code EntitySit} / {@code EntityBroom}
     * 都还没移植，所以那份豁免表暂时是空的。不清的话会出现「关掉了却还被抱在怀里」，
     * 只能靠潜行右键（{@code tryDismount}）补救。</p>
     */
    /**
     * 服务端写入 {@code ModelId}。
     *
     * <p>校验三件事：女仆归请求者所有、模型 id 能解析成合法 {@code ResourceLocation}、
     * 长度合理。上游只做第一项；这里多挡一层，是因为这个包完全是玩家可控的 ——
     * 写进一个随意的长字符串会在渲染时反复尝试解析并刷日志。</p>
     */
    private static void handleModel(ServerPlayer player, String modelId, int entityId) {
        EntityMaid maid = findOwnedMaid(player, entityId);
        if (maid == null || modelId == null || modelId.isEmpty() || modelId.length() > 128) {
            return;
        }
        if (!net.minecraft.resources.ResourceLocation.isValidResourceLocation(modelId)) {
            return;
        }
        maid.setModelId(modelId);
        // 「换个模样」进度（上游 MaidModelMessage 在写入成功后打判据）
        InitTrigger.MAID_EVENT.trigger(player, TriggerType.CHANGE_MAID_MODEL);
    }

    private static void handleRideable(ServerPlayer player, boolean value, int entityId) {
        EntityMaid maid = findOwnedMaid(player, entityId);
        if (maid == null) {
            return;
        }
        if (maid.isRideable() == value) {
            return;
        }
        maid.setRideable(value);
        if (!value && maid.getVehicle() != null) {
            maid.stopRiding();
        }
    }

    /**
     * 切换日程表，逻辑对齐上游 {@code MaidConfigMessage#handle} 里那一段。
     *
     * <p>四件事，缺一不可：</p>
     * <ol>
     *     <li>{@code setSchedule} —— 写同步数据并<b>重建大脑</b>（作息表换了，活动表也要换）；</li>
     *     <li>{@code getSchedulePos().restrictTo} —— 把活动范围挪到新作息对应的那个点；</li>
     *     <li>开着居家模式时给她一个走过去的目标，否则她会站在原地直到下一次日程刷新；</li>
     *     <li>触发 {@code switch_schedule} 进度判据。</li>
     * </ol>
     *
     * <p>第 2 步是本移植版最容易漏的一点：只改 {@code MaidSchedule} 而不动
     * {@code SchedulePos}，女仆的活动范围还是按旧作息算的，表现为
     * 「切了夜班但白天照样在外面干活」。</p>
     */
    private static void handleSchedule(ServerPlayer player, MaidSchedule schedule, int entityId) {
        EntityMaid maid = findOwnedMaid(player, entityId);
        if (maid == null) {
            return;
        }
        if (maid.getSchedule() == schedule) {
            return;
        }
        maid.setSchedule(schedule);
        maid.getSchedulePos().restrictTo(maid);
        if (maid.isHomeModeEnable()) {
            BehaviorUtils.setWalkAndLookTargetMemories(maid, maid.getRestrictCenter(), 0.7f, 3);
        }
        InitTrigger.MAID_EVENT.trigger(player, TriggerType.SWITCH_SCHEDULE);
    }
}

