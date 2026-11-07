package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

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
                        case RIDEABLE -> {
                            boolean value = buf.readBoolean();
                            server.execute(() -> handleRideable(player, value, entityId));
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
            case HOME -> applyHome(maid, value);
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
                .ifPresent(maid::setTask);
    }

    /**
     * 切换居家模式，并<b>同时维护工作范围中心</b>。
     *
     * <p>这是「开了居家模式后女仆站着不动」的根因：{@code hasRestriction()} 返回
     * {@code isHomeModeEnable()}，一旦为 {@code true}，所有「是否在工作范围内」的判定
     * （{@code isWithinRestriction} / {@code searchDimension} / 寻路目标点）都会生效。
     * 而工作范围中心默认是 {@code BlockPos.ZERO}（世界原点 y=0），女仆实际可能站在 y=-60，
     * 距离远超半径 —— 于是她认为自己「一直在界外」，既不走路也不干活。</p>
     *
     * <p>上游在 {@code SchedulePos#setHomeModeEnable(maid, maid.blockPosition())} 里
     * 把中心设为女仆当前位置。本移植版等价处理：开启时以当前位置为中心、半径为
     * 配置 {@code MAID_WORK_RANGE}；关闭时把半径还原成 {@code MAID_NON_HOME_RANGE}
     * （此时 {@code hasRestriction()} 为 false，中心值只是占位）。</p>
     */
    private static void applyHome(EntityMaid maid, boolean enable) {
        if (enable) {
            // 上游 SchedulePos#setHomeModeEnable 的语义：只有「第一次开启」才写入工作点，
            // 之后关掉再开不会把位置挪到脚下 —— 指南针的区域因此始终是「当初设定的那一块」。
            BlockPos homePos = maid.getHomePos();
            if (!maid.hasHomePos()) {
                homePos = maid.blockPosition();
                maid.setHomePos(homePos);
            }
            maid.restrictTo(homePos, MaidConfig.workRange());
            maid.setHomeModeEnable(true);
        } else {
            maid.restrictTo(BlockPos.ZERO, MaidConfig.nonHomeRange());
            maid.setHomeModeEnable(false);
            // 与上游 SchedulePos#clear 等价：关掉居家模式就把锚点一起「忘掉」。
            //
            // 上游的 setHomeModeEnable 只在 configured == false 时写入工作点，
            // 而 clear(maid) 正是把 configured 置回 false 的那个入口。
            // 少了这一步，玩家把女仆挪到新地方再开居家模式，锚点仍然是老位置 ——
            // 表现就是「指南针画出的圈留在原地不动」。
            maid.setHomePos(BlockPos.ZERO);
            // 关掉居家后清掉寻路目标，否则她还会朝旧的中心点走一段
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
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
}

