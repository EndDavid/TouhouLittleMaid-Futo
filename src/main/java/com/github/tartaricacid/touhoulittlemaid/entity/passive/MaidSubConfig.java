package com.github.tartaricacid.touhoulittlemaid.entity.passive;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;

/**
 * 女仆子配置，对应上游 {@code entity/passive/MaidConfigManager} 里的
 * {@code MaidSubConfig} 那一组字段（女仆配置界面上的 8 行）。
 *
 * <p><b>为什么单独成一个类：</b>这 8 项彼此独立、没有任何业务逻辑，
 * 全是「同步数据 + 存档」的样板代码。塞进 {@code EntityMaid}（已经两千多行）
 * 只会让人更难找到真正有逻辑的部分，所以在这里集中管理，
 * {@code EntityMaid} 只保留一行 {@code MaidSubConfig.define(...)} 与读写 NBT 的两个调用。</p>
 *
 * <p><b>与上游的差异：</b>上游把这些值放在能力（capability）里的一个可序列化对象中，
 * 由 {@code MaidConfigManager} 转发；Fabric 侧没有 Forge capability，因此直接挂在实体的
 * {@code SynchedEntityData} 上 —— 语义完全一致（都是「服务端写、两端读、随存档持久化」），
 * 而且少一层转发。</p>
 */
public final class MaidSubConfig {
    /**
     * 是否渲染背包（上游 {@code BackpackShow}）。
     */
    private static final EntityDataAccessor<Boolean> SHOW_BACKPACK =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.BOOLEAN);
    /**
     * 是否渲染背后的手持物品（上游 {@code BackItemShow}）。
     */
    private static final EntityDataAccessor<Boolean> SHOW_BACK_ITEM =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.BOOLEAN);
    /**
     * 是否显示头顶聊天气泡（上游 {@code ChatBubbleShow}）。
     */
    private static final EntityDataAccessor<Boolean> SHOW_CHAT_BUBBLE =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.BOOLEAN);
    /**
     * 女仆语音频率，0.0 ~ 1.0（上游 {@code SoundFreq}）。
     *
     * <p>上游在客户端用 {@code PlaySoundEvent} 拦截语音，再按
     * 「本值 × 全局频率」的概率丢弃。Fabric 1.18.2 没有等价事件，所以本移植版把同一个
     * 判定提到服务端发声处（{@code EntityMaid#shouldPlayVoice()}）：女仆语音本来就是服务端
     * 下发 {@code ClientboundSoundPacket} 播放的，在服务端拦掉，客户端连包都收不到，
     * 效果等价而且更省带宽。</p>
     *
     * <p>它管的是<b>所有</b>女仆语音（环境音、攻击音、受伤、死亡、拾取、驯服），
     * 不只是待机碎碎念 —— 与上游 {@code MaidSoundInstance} 的覆盖面一致。</p>
     */
    private static final EntityDataAccessor<Float> SOUND_FREQ =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.FLOAT);
    /**
     * 能否自己开门（上游 {@code OpenDoor}）。
     */
    private static final EntityDataAccessor<Boolean> OPEN_DOOR =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.BOOLEAN);
    /**
     * 能否自己开栅栏门（上游 {@code OpenFenceGate}）。
     */
    private static final EntityDataAccessor<Boolean> OPEN_FENCE_GATE =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.BOOLEAN);
    /**
     * 能否主动爬梯子（上游 {@code ActiveClimbing}）。
     */
    private static final EntityDataAccessor<Boolean> ACTIVE_CLIMBING =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.BOOLEAN);
    /**
     * 能否被骑乘（上游 {@code MaidConfigManager} 的 {@code DATA_RIDEABLE}）。
     *
     * <p><b>注意它不是女仆配置界面上的那 8 行之一</b>：上游把它放在
     * {@code MaidConfigManager} 的<b>根</b>字段里（与「拾取」「居家」同级），
     * 由主界面左下角那个「抱」按钮切换。这里为了少一个类仍然寄放在本类，
     * 但存档位置与上游一致 —— 写在父标签下、标签名 {@code MaidIsRideable}。</p>
     *
     * <p>它的语义是「女仆允不允许成为任何东西的乘客」（上游 {@code MaidMountEvent}
     * 在 {@code EntityMountEvent} 上拦截），不只是鞍。关掉以后女仆既上不了鞍，
     * 也坐不上船和矿车。</p>
     */
    private static final EntityDataAccessor<Boolean> RIDEABLE =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.BOOLEAN);

    /**
     * 存档里的子标签名，与上游一致。
     */
    private static final String TAG = "MaidSubConfig";
    /**
     * 「能否被骑乘」的存档标签，与上游一致 —— 注意它在<b>父</b>标签下，不在 {@link #TAG} 里面。
     */
    private static final String RIDEABLE_TAG = "MaidIsRideable";

    private MaidSubConfig() {
    }

    /**
     * 注册同步数据。必须在 {@code EntityMaid#defineSynchedData} 里调用，
     * 而且只能调用一次 —— {@code SynchedEntityData#define} 对同一个 id 重复注册会抛异常。
     */
    public static void define(SynchedEntityData data) {
        data.define(SHOW_BACKPACK, true);
        data.define(SHOW_BACK_ITEM, true);
        data.define(SHOW_CHAT_BUBBLE, true);
        data.define(SOUND_FREQ, 1.0F);
        data.define(OPEN_DOOR, true);
        data.define(OPEN_FENCE_GATE, true);
        data.define(ACTIVE_CLIMBING, true);
        data.define(RIDEABLE, true);
    }

    public static boolean isShowBackpack(EntityMaid maid) {
        return maid.getEntityData().get(SHOW_BACKPACK);
    }

    public static void setShowBackpack(EntityMaid maid, boolean value) {
        maid.getEntityData().set(SHOW_BACKPACK, value);
    }

    public static boolean isShowBackItem(EntityMaid maid) {
        return maid.getEntityData().get(SHOW_BACK_ITEM);
    }

    public static void setShowBackItem(EntityMaid maid, boolean value) {
        maid.getEntityData().set(SHOW_BACK_ITEM, value);
    }

    public static boolean isShowChatBubble(EntityMaid maid) {
        return maid.getEntityData().get(SHOW_CHAT_BUBBLE);
    }

    public static void setShowChatBubble(EntityMaid maid, boolean value) {
        maid.getEntityData().set(SHOW_CHAT_BUBBLE, value);
    }

    public static float getSoundFreq(EntityMaid maid) {
        return Mth.clamp(maid.getEntityData().get(SOUND_FREQ), 0.0F, 1.0F);
    }

    /**
     * 设置语音频率，自动夹到 0.0 ~ 1.0。
     *
     * <p>夹取放在写入端而不是读取端：GUI 每点一次加/减 0.1，浮点累加会产生
     * 0.30000001 这种值，播放次数一多就会溢出到区间外。</p>
     */
    public static void setSoundFreq(EntityMaid maid, float value) {
        maid.getEntityData().set(SOUND_FREQ, Mth.clamp(value, 0.0F, 1.0F));
    }

    public static boolean canOpenDoor(EntityMaid maid) {
        return maid.getEntityData().get(OPEN_DOOR);
    }

    public static void setOpenDoor(EntityMaid maid, boolean value) {
        maid.getEntityData().set(OPEN_DOOR, value);
    }

    public static boolean canOpenFenceGate(EntityMaid maid) {
        return maid.getEntityData().get(OPEN_FENCE_GATE);
    }

    public static void setOpenFenceGate(EntityMaid maid, boolean value) {
        maid.getEntityData().set(OPEN_FENCE_GATE, value);
    }

    public static boolean isActiveClimbing(EntityMaid maid) {
        return maid.getEntityData().get(ACTIVE_CLIMBING);
    }

    public static void setActiveClimbing(EntityMaid maid, boolean value) {
        maid.getEntityData().set(ACTIVE_CLIMBING, value);
    }

    public static boolean isRideable(EntityMaid maid) {
        return maid.getEntityData().get(RIDEABLE);
    }

    public static void setRideable(EntityMaid maid, boolean value) {
        maid.getEntityData().set(RIDEABLE, value);
    }

    /**
     * 写存档。字段名与上游逐字对应，方便与 Forge 版存档互相读。
     */
    public static void save(CompoundTag parent, EntityMaid maid) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("BackpackShow", isShowBackpack(maid));
        tag.putBoolean("BackItemShow", isShowBackItem(maid));
        tag.putBoolean("ChatBubbleShow", isShowChatBubble(maid));
        tag.putFloat("SoundFreq", getSoundFreq(maid));
        tag.putBoolean("OpenDoor", canOpenDoor(maid));
        tag.putBoolean("OpenFenceGate", canOpenFenceGate(maid));
        tag.putBoolean("ActiveClimbing", isActiveClimbing(maid));
        parent.put(TAG, tag);
        // 「能否被骑乘」按上游写在同一层，不在 MaidSubConfig 子标签里
        parent.putBoolean(RIDEABLE_TAG, isRideable(maid));
    }

    /**
     * 读存档。
     *
     * <p>每个字段都带默认值兜底：旧存档（或 Forge 版存档）里缺项时用上游默认值，
     * 不抛异常。缺项就整段跳过的写法会让「只补了一部分字段」的存档读成全 false，
     * 表现是女仆进门不会开门、走路也不会爬梯子，很难排查。</p>
     */
    public static void load(CompoundTag parent, EntityMaid maid) {
        // 未骑乘开关是根字段，即便没有子标签也要读
        setRideable(maid, !parent.contains(RIDEABLE_TAG) || parent.getBoolean(RIDEABLE_TAG));
        if (!parent.contains(TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag tag = parent.getCompound(TAG);
        setShowBackpack(maid, !tag.contains("BackpackShow") || tag.getBoolean("BackpackShow"));
        setShowBackItem(maid, !tag.contains("BackItemShow") || tag.getBoolean("BackItemShow"));
        setShowChatBubble(maid, !tag.contains("ChatBubbleShow") || tag.getBoolean("ChatBubbleShow"));
        setSoundFreq(maid, tag.contains("SoundFreq") ? tag.getFloat("SoundFreq") : 1.0F);
        setOpenDoor(maid, !tag.contains("OpenDoor") || tag.getBoolean("OpenDoor"));
        setOpenFenceGate(maid, !tag.contains("OpenFenceGate") || tag.getBoolean("OpenFenceGate"));
        setActiveClimbing(maid, !tag.contains("ActiveClimbing") || tag.getBoolean("ActiveClimbing"));
    }
}

