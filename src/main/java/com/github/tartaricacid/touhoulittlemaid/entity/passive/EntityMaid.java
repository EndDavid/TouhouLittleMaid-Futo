package com.github.tartaricacid.touhoulittlemaid.entity.passive;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.backpack.IMaidBackpack;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import com.github.tartaricacid.touhoulittlemaid.capability.MaidNumCapability;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.backpack.BackpackManager;
import com.github.tartaricacid.touhoulittlemaid.entity.backpack.EmptyBackpack;
import com.github.tartaricacid.touhoulittlemaid.entity.data.AttackListData;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskIdle;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.mojang.serialization.Dynamic;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.EntityArmorInvWrapper;
import net.minecraftforge.items.wrapper.EntityHandsInvWrapper;
import net.minecraftforge.items.wrapper.RangedWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 女仆实体。
 *
 * <p>驯服 / 坐下逻辑按上游 TLM 的实现移植。坐姿状态在 1.18.2 里由两部分组成，
 * 这里必须先说清楚，否则很容易写错：</p>
 * <ul>
 *     <li>{@code TamableAnimal.DATA_FLAGS_ID} 的 bit0 是<b>同步</b>的「坐姿位」，
 *         由 {@link #isInSittingPose()} / {@link #setInSittingPose(boolean)} 读写；
 *         客户端动画、其它实体逻辑都只看它。</li>
     *     <li>{@code TamableAnimal.orderedToSit} 是<b>不同步</b>的普通字段（只进存档
 *         {@code Sitting}），即「玩家下令坐下」。</li>
 * </ul>
 *
 * <p>上游把两者绑在同一个 setter 里（{@link #setInSittingPose(boolean)}），所以本移植版
 * 不再使用原版 {@code SitWhenOrderedToGoal}：它的 {@code stop()} 会调
 * {@code setInSittingPose(false)}，等于把玩家的「坐下指令」一起抹掉，女仆从此忘记自己该坐下
     * （存档里 {@code Sitting} 也会写成 false）。现在和上游一样，行为全部交给
 * {@link MaidBrain}（Brain 活动系统），坐姿位只由玩家交互 / 存档 / 落水保险改变，
 * 而「坐下就禁止移动」由 {@link #canBrainMoving()} 表达。</p>
 */
public class EntityMaid extends TamableAnimal implements IMaid,
        com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity.MaidModelHolder,
        net.minecraft.world.entity.monster.CrossbowAttackMob {
    /**
     * 默认模型，与上游 {@code EntityMaid.DEFAULT_MODEL_ID} 一致：
     * {@code touhou_little_maid:hakurei_reimu}（灵梦）。
     *
     * <p>它是旧版（1.10.0）模型，由 {@code GeckoModelLoader} 惰性构建并做骨骼名映射后，
     * 复用基岩动画管线渲染，因此默认外观与 Fabric 1.20.1 TLMO / Forge 1.18.2 一致。</p>
     */
    public static final String DEFAULT_MODEL_ID = "touhou_little_maid:hakurei_reimu";

    /**
     * 女仆 NBT 里「模型 id」的键名。
     *
     * <p>同步数据用的是 {@code DATA_MODEL_ID}，但落盘 / 物品 NBT（胶片的
     * {@code MaidInfo}、手办的 {@code EntityInfo}）里用的都是这个字符串键。
     * 上游把它暴露成 {@code EntityMaid.MODEL_ID_TAG}，雕像 / 手办 / 胶片都引它，
     * 所以这里也必须是 public 常量，不能各写各的字面量。</p>
     */
    public static final String MODEL_ID_TAG = "ModelId";

    /**
     * 渲染状态，默认 {@link MaidRenderState#ENTITY}。
     *
     * <p>雕像与手办把实体 NBT 读进一个不参与 tick 的临时实体来渲染，
     * 读到的是「快照」而不是本体；置成 {@code STATUE} / {@code GARAGE_KIT}
     * 让渲染链路上需要区分的地方有据可依，同时也是给附属模组留的公开字段。</p>
     */
    public com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState renderState =
            com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState.ENTITY;

    /**
     * 当前外观的模型 id（同步数据）。默认值就是兜底模型灵梦，
     * 真正的随机在 {@code finalizeSpawn} 里由 {@code ServerModelPool} 挑一个写进来。
     */
    private static final EntityDataAccessor<String> DATA_MODEL_ID =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_FAVORABILITY =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PICKUP =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.BOOLEAN);
    /**
     * 当前工作模式的 uid（对应上游 {@code DATA_TASK}）。
     */
    private static final EntityDataAccessor<String> DATA_TASK =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.STRING);
    /**
     * 撒娇姿态：Brain 里由 {@code MaidBegTask} 置位，动画层据此播放 {@code beg}。
     */
    private static final EntityDataAccessor<Boolean> DATA_BEGGING =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.BOOLEAN);
    /**
     * 日程表模式：白天班 / 夜班 / 全天。
     */
    private static final EntityDataAccessor<MaidSchedule> SCHEDULE_MODE =
            SynchedEntityData.defineId(EntityMaid.class, MaidSchedule.DATA);
    /**
     * 工作范围中心与半径（对应上游 {@code RESTRICT_CENTER} / {@code RESTRICT_RADIUS}）。
     */
    private static final EntityDataAccessor<BlockPos> RESTRICT_CENTER =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> RESTRICT_RADIUS =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.FLOAT);
    /**
     * 举弓 / 举盾姿态（上游 {@code DATA_ARM_RISE}）。
     */
    private static final EntityDataAccessor<Boolean> DATA_ARM_RISE =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.BOOLEAN);
    /**
     * 弩是否已装满弹药（原版 {@code CrossbowAttackMob} 的状态位）。
     *
     * <p>原版动画与 {@code CrossbowAttack} 行为都靠它判断要不要继续拉弩。</p>
     */
    private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING_CROSSBOW =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.BOOLEAN);
    /**
 * 当前背包类型的 id（对应上游 {@code BACKPACK_TYPE}）。
     *
     * <p>必须同步：客户端要用它决定 Molang 的 {@code tlm_has_backpack}
     * 以及后续的背包渲染层；交互判定也需要两侧看到同一个背包。</p>
     */
    private static final EntityDataAccessor<String> DATA_BACKPACK_TYPE =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.STRING);
    /**
     * 传送到主人身边的最大尝试次数（上游同名常量）。
     */
    private static final int MAX_TELEPORT_ATTEMPTS_TIMES = 10;
    /**
     * 非居家模式下的默认工作范围半径，读配置 {@code MaidConfig.MAID_NON_HOME_RANGE}
     * （上游默认 8）。字段本身保留为常量名，取值统一走 {@link #defaultRestrictRadius()}，
     * 这样配置改动后无需重建实体。
     */
    private static final float DEFAULT_RESTRICT_RADIUS = 8.0F;
    /**
     * 女仆物品栏固定 36 格；实际可用格数由当前背包决定
     * （见 {@link #getAvailableBackpackInv()}）。
     */
    private static final int MAID_INVENTORY_SIZE = 36;
    private static final String MAID_INVENTORY_TAG = "MaidInventory";
    /**
     * Bauble inventory save key (upstream EntityMaid.MAID_BAUBLE_INVENTORY_TAG).
     */
    public static final String MAID_BAUBLE_INVENTORY_TAG = "MaidBaubleInventory";

    private final ItemStack[] handItemsForAnimation = new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY};
    /**
     * 居家模式（对应上游 {@code DATA_HOME_MODE}）。
     *
     * <p><b>必须同步</b>：GUI 上的居家开关按钮读的就是这个值。
     * 早期版本用的是普通字段，服务端改了客户端永远看不到 ——
     * 表现就是「点了 Home 按钮完全没反应，重新打开界面又变回关闭」。</p>
     */
    private static final EntityDataAccessor<Boolean> DATA_HOME_MODE =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.BOOLEAN);

    /**
     * 拾取类型（对应上游 {@code configManager.getPickupType()}）。
     *
     * <p>存字符串而不是序号：枚举以后可能增删，序号会串位，字符串不会。
     * 必须同步：客户端要靠它决定拾取气泡 / 提示。</p>
     */
    private static final EntityDataAccessor<String> DATA_PICKUP_TYPE =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.STRING);

    /**
     * 攻击名单，对应上游 {@code EntityMaid#getData(InitTaskData.ATTACK_LIST)}。
     *
     * <p>只影响「攻击 / 弓兵 / 弩兵 / 弹幕攻击 / 三叉戟攻击」这类 {@code IAttackTask}：
     * 名单里出现过的生物按名单里的关系判定，没出现的仍走默认规则。
     * 因此空名单 = 完全按原版规则办事，这也是默认值。</p>
     */
    private AttackListData attackList = AttackListData.empty();

    public AttackListData getAttackList() {
        return this.attackList;
    }

    /**
     * 整份替换攻击名单（服务端收到「任务设置」界面的保存包时调用）。
     */
    public void setAttackList(@Nullable AttackListData data) {
        this.attackList = data == null ? AttackListData.empty() : data;
    }


    /**
     * 拾取音效节流：每捡到 5 次物品播一次，与上游一致。
     */
    /**
     * 女仆经验值的存档键（上游 {@code EntityMaid.EXPERIENCE_TAG}）。
     */
    public static final String EXPERIENCE_TAG = "MaidExperience";

    /**
     * 女仆经验值（上游 {@code DATA_EXPERIENCE}）。
     *
     * <p><b>必须是同步数据，也必须是 int。</b>上游就是
     * {@code SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.INT)}，
     * 原因有两条：</p>
     * <ol>
     *     <li>界面左侧那条经验条读的就是它，客户端拿不到就永远是空的
     *         —— 表现为「女仆吸收经验球以后什么都没发生」；</li>
     *     <li>附魔之瓶兑换（拿玻璃瓶右击）按 {@code getExperience() / 12} 算瓶数，
     *         若只是普通字段，读档后直接归零，攒的经验白攒。</li>
     * </ol>
     */
    private static final EntityDataAccessor<Integer> DATA_EXPERIENCE =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.INT);
    /**
     * 谁正抱着她（玩家实体 id，{@code 0} = 没被抱）。
     *
     * <p><b>为什么要有这个字段：</b>乘客的坐标<b>不由服务端同步</b> —— 客户端是靠
     * {@code rideTick → positionRider} 自己算的。一旦客户端那条路径没走到，
     * 画面就会永远停在抱起那一刻，直到放下才被位置包拽回来。
     * 把「谁抱着她」也同步给客户端，客户端就能<b>不依赖乘客关系</b>自己把位置算出来。</p>
     */
    private static final EntityDataAccessor<Integer> DATA_CARRIER_ID =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.INT);
    /**
     * 作息坐标：工作点 / 娱乐点 / 睡觉点 + 它们所属的维度
     * （上游 {@code entity/passive/SchedulePos}）。
     *
     * <p><b>为什么不再用一个同步的「居家位置」字段：</b>上游的居家模式不是一个点，
     * 而是三个。日程表在 {@code WORK} / {@code IDLE} / {@code REST} 之间切换时，
     * 活动范围要跟着挪到对应的点上（{@code SchedulePos#restrictTo}）。早期版本把它们
     * 压成一个 {@code DATA_HOME_POS}，于是「工作 12 格 / 娱乐 6 格 / 睡觉 6 格」
     * 三个圈永远同心 —— 河童的罗盘写下的三个点里只有一个能真正生效。</p>
     *
     * <p><b>所以它也不再是同步数据。</b>三个坐标只在服务端参与 AI 判定；
     * 客户端要看区域时由 {@code network/MaidAreaPacket} 按需发一份
     * （上游 {@code SyncMaidAreaMessage} 同款），而不是让每只女仆常驻同步三个 BlockPos。</p>
     */
    private final SchedulePos schedulePos;

    private int pickupSoundCount = 5;
    /**
     * 换装冷却（上游 {@code backpackDelay}）：穿上 / 脱下背包后 20 tick 内不再响应，
      * 避免一次点击被主副手各处理一次而「来回换装」。
     */
    private int backpackDelay;

    /**
     * 女仆物品栏（36 格），前 N 格可用，N 由背包决定。
     */
    private final ItemStackHandler maidInv = new ItemStackHandler(MAID_INVENTORY_SIZE);
    private final EntityArmorInvWrapper armorInvWrapper = new EntityArmorInvWrapper(this);
    private final EntityHandsInvWrapper handsInvWrapper = new EntityHandsInvWrapper(this);

    /**
     * Maid bauble inventory (upstream EntityMaid#maidBauble).
     * Fixed at BAUBLE_INV_SIZE slots = the 3x3 grid in the GUI; upstream hardcodes 9 too.
     */
    public static final int BAUBLE_INV_SIZE = 9;
    private final com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler maidBauble =
            new com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler(BAUBLE_INV_SIZE);

    public EntityMaid(EntityType<? extends EntityMaid> type, Level level) {
        super(type, level);
        this.setTame(false);
        this.setPersistenceRequired();
        // 上游同款：初始「家」= 当前位置，维度取实体自己的维度。
        // 这样一只没被罗盘配置过的女仆打开 Home 后仍然有一个有效的工作点。
        this.schedulePos = new SchedulePos(BlockPos.ZERO, level.dimension().location());
    }

    /**
     * Attributes, copied field for field from upstream EntityMaid#createAttributes.
     *
     * <p>Upstream is:</p>
     * <pre>
     * LivingEntity.createLivingAttributes()      // five attributes, all default values
     *     .add(Attributes.FOLLOW_RANGE, 64)
     *     .add(Attributes.ATTACK_KNOCKBACK)
     *     .add(Attributes.ATTACK_DAMAGE)
     *     .add(Attributes.LUCK)</pre>
     *
     * <p>The point is what it does NOT override: MOVEMENT_SPEED stays at the vanilla
     * generic.movement_speed default of 0.7. This port used to write
     * .add(Attributes.MOVEMENT_SPEED, 0.35D), which halved the base speed of every
     * single movement instruction - every AI task computes
     * speed * MOVEMENT_SPEED, so identical multipliers on a halved base meant the maid
     * moved at half upstream speed everywhere. It is most obvious when she runs a long
     * stretch toward an owner holding cake.</p>
     *
     * <p>MAX_HEALTH is not written here either: the vanilla default is already 20, and
     * FavorabilityManager#refreshAttributes resets the base value from the favorability
     * level afterwards, so pinning it here would only hide that layer.</p>
     */
    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.ATTACK_KNOCKBACK)
                .add(Attributes.ATTACK_DAMAGE)
                .add(Attributes.LUCK);
    }

    /**
     * 女仆不做自然生成，仅通过刷怪蛋 / 结构获得，与上游一致。
     */
    public static boolean checkMaidSpawnRules(EntityType<EntityMaid> type, ServerLevelAccessor level,
                                              MobSpawnType reason, BlockPos pos, java.util.Random random) {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_MODEL_ID, DEFAULT_MODEL_ID);
        this.entityData.define(DATA_FAVORABILITY, 0);
        this.entityData.define(DATA_EXPERIENCE, 0);
        this.entityData.define(DATA_CARRIER_ID, 0);
        this.entityData.define(DATA_PICKUP, false);
        this.entityData.define(DATA_HOME_MODE, false);
        this.entityData.define(DATA_PICKUP_TYPE, PickType.ALL.getId());
        MaidSubConfig.define(this.entityData);
        // 对局记录的两个同步位：五子棋胜场数据与这一局的胜负。
        // 必须走静态方法：本方法在 Entity 的构造器里就会被调到，
        // 那时候本类的实例字段（包括 gameRecordManager）全都还是 null。
        MaidGameRecordManager.defineSynchedData(this.entityData);

        this.entityData.define(DATA_TASK, TaskIdle.UID.toString());
        this.entityData.define(DATA_BEGGING, false);
        this.entityData.define(SCHEDULE_MODE, MaidSchedule.DAY);
        this.entityData.define(RESTRICT_CENTER, BlockPos.ZERO);
        this.entityData.define(RESTRICT_RADIUS, defaultRestrictRadius());
        this.entityData.define(DATA_ARM_RISE, false);
        this.entityData.define(DATA_IS_CHARGING_CROSSBOW, false);

        // 用 EmptyBackpack.ID 而不是 BackpackManager.getEmptyBackpack()：
        // defineSynchedData 可能早于 BackpackManager.init()（例如注册阶段创建原型实体）
        this.entityData.define(DATA_BACKPACK_TYPE, EmptyBackpack.ID.toString());
    }

    @Override
    protected void registerGoals() {
        // 与上游一致兜底语义致：女仆的行为全部由 Brain（{@link MaidBrain}）驱动，goalSelector 不再承担 AI。
 // 唯独保留 FloatGoal：游泳相关行为（MaidSwimJumpTask / MaidBreathAirTask）还没移植，
        // 没有它女仆落水后会一直往下沉。等游泳行为补齐后即可移除。
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    /**
     * 换成女仆专用寻路导航（上游 {@code EntityMaid#createNavigation}）。
     *
     * <p>原版的 {@code GroundPathNavigation} 不会开门、也不认梯子；女仆配置界面里的
     * 「能否开门 / 能否开栅栏门 / 能否主动爬梯」这三个开关要生效，必须换掉导航对象。</p>
     */
    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        return new com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation.MaidPathNavigation(this, level);
    }



    // ---------------------------------------------------------------- Brain

    @Override
    @SuppressWarnings("unchecked")
    public Brain<EntityMaid> getBrain() {
        return (Brain<EntityMaid>) super.getBrain();
    }

    @Override
    protected net.minecraft.world.entity.ai.Brain.Provider<EntityMaid> brainProvider() {
        return Brain.provider(MaidBrain.getMemoryTypes(), MaidBrain.getSensorTypes());
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        Brain<EntityMaid> brain = this.brainProvider().makeBrain(dynamic);
        MaidBrain.registerBrainGoals(brain, this);
        return brain;
    }

    /**
     * 切换 Task / 日程后重建行为树（上游 {@code EntityMaid#refreshBrain}）。
     */
    public void refreshBrain(ServerLevel serverLevel) {
        Brain<EntityMaid> brain = this.getBrain();
        brain.stopAll(serverLevel, this);
        this.brain = brain.copyWithoutBehaviors();
        MaidBrain.registerBrainGoals(this.getBrain(), this);
    }

    @Override
    protected void customServerAiStep() {
        this.level.getProfiler().push("maidBrain");
        this.getBrain().tick((ServerLevel) this.level, this);
        this.level.getProfiler().pop();
        super.customServerAiStep();
    }

    // ---------------------------------------------------------------- Task / 日程表 / 工作范围

    @Override
    public IMaidTask getTask() {
        ResourceLocation uid = parseId(this.entityData.get(DATA_TASK));
        if (uid == null) {
            return TaskManager.getIdleTask();
        }
        return TaskManager.findTask(uid).orElse(TaskManager.getIdleTask());
    }

    public void setTask(IMaidTask task) {
        if (task == this.getTask()) {
            return;
        }
        this.entityData.set(DATA_TASK, task.getUid().toString());
        if (this.level instanceof ServerLevel serverLevel) {
            this.refreshBrain(serverLevel);
        }
    }

    public MaidSchedule getSchedule() {
        return this.entityData.get(SCHEDULE_MODE);
    }

    public void setSchedule(MaidSchedule schedule) {
        this.entityData.set(SCHEDULE_MODE, schedule);
        if (this.level instanceof ServerLevel serverLevel) {
            this.refreshBrain(serverLevel);
        }
    }

    /**
     * 当前时刻应该做什么（工作 / 娱乐 / 休息），由日程表推出。
     */
    public Activity getScheduleDetail() {
        int time = (int) (this.level.getDayTime() % 24000L);
        return switch (this.getSchedule()) {
            case ALL -> Activity.WORK;
            case NIGHT -> com.github.tartaricacid.touhoulittlemaid.init.InitEntities
                    .MAID_NIGHT_SHIFT_SCHEDULES.getActivityAt(time);
            default -> com.github.tartaricacid.touhoulittlemaid.init.InitEntities
                    .MAID_DAY_SHIFT_SCHEDULES.getActivityAt(time);
        };
    }

    public boolean isHomeModeEnable() {
        return this.entityData.get(DATA_HOME_MODE);
    }

    public void setHomeModeEnable(boolean enable) {
        this.entityData.set(DATA_HOME_MODE, enable);
    }

    /**
     * 作息坐标（上游 {@code EntityMaid#getSchedulePos}）。
     *
     * <p>读它是安全的：字段在构造函数里就初始化了，读档只会覆盖内容。
     * 写它之前请想清楚 —— 三个点各有各的用途，随手改一个点会让女仆
     * 在工作时间和睡觉时间跑向同一个位置。</p>
     */
    public SchedulePos getSchedulePos() {
        return this.schedulePos;
    }

    /**
     * 工作范围中心（上游同名字段，见 {@code SchedulePos.restrictTo}）。
     *
     * <p>它就是 vanilla {@code Mob#restrictCenter}，由
     * {@code SchedulePos#restrictTo} 按当前活动换成工作点 / 娱乐点 / 睡觉点之一。
     * 注意它<b>随时会变</b>：想读「玩家当初设置的坐标」请去
     * {@link #getSchedulePos()}，不要读这个缓存出来的活动中心。</p>
     */
    public BlockPos getRestrictCenter() {
        return this.entityData.get(RESTRICT_CENTER);
    }

    /**
     * 工作范围半径（上游同名字段，见 {@code SchedulePos.restrictTo}）。
     */
    public float getRestrictRadius() {
        return this.entityData.get(RESTRICT_RADIUS);
    }

    @Override
    public void restrictTo(BlockPos pos, int distance) {
        this.entityData.set(RESTRICT_CENTER, pos);
        this.entityData.set(RESTRICT_RADIUS, (float) distance);
    }

    /**
     * 清除工作范围限制（上游同名方法）。
     *
     * <p>恢复到默认值，通常是以出生点为中心的 8 格半径。</p>
     */
    @Override
    public void clearRestriction() {
        // 上游同款：清空作息坐标（三个点并回工作点、configured 置回 false），
        // 于是下一次开启居家模式会以她当时的站位重新锚定。
        this.schedulePos.clear(this);
    }

    @Override
    public boolean hasRestriction() {
        return this.isHomeModeEnable();
    }

    @Override
    public boolean isWithinRestriction() {
        return this.isWithinRestriction(this.blockPosition());
    }

    @Override
    public boolean isWithinRestriction(BlockPos pos) {
        if (this.hasRestriction()) {
            return this.getRestrictCenter().distSqr(pos)
                    < (double) (this.getRestrictRadius() * this.getRestrictRadius());
        }
        return true;
    }

    /**
     * 女仆此刻是否允许移动（上游同名方法）：坐下 / 骑乘 / 睡觉 / 被拴住时都不动。
     */
    public boolean canBrainMoving() {
        // 被抱着时也不动：玩家已经在直接控制她的位置了，
        // 再让 Brain 驱动寻路会和 updateCarriedPosition 打架（表现为抖动 / 推着玩家走）。
        return !this.isMaidInSittingPose() && !this.isPassenger() && !this.isSleeping()
                && !this.isLeashed() && !this.isCarried();
    }

    /**
 * 按「工作范围」划定实体搜索盒（上游 {@code EntityMaid#searchDimension}）。
 * 只有工作时间才用当前 Task 的范围，其余时间用空闲模式的范围，避免性能压力。
     */
    public AABB searchDimension() {
        if (this.getScheduleDetail() == Activity.WORK) {
            return this.getTask().searchDimension(this);
        }
        return TaskManager.getIdleTask().searchDimension(this);
    }

    public float searchRadius() {
        return this.getTask().searchRadius(this);
    }

    public boolean canPathReach(BlockPos pos) {
        var path = this.getNavigation().createPath(pos, 0);
        return path != null && path.canReach();
    }

    public boolean canPathReach(Entity entity) {
        var path = this.getNavigation().createPath(entity, 0);
        return path != null && path.canReach();
    }

    /**
     * 传送到主人身边（上游同名方法），最多尝试 {@value #MAX_TELEPORT_ATTEMPTS_TIMES} 次。
     */
    public boolean teleportToOwner(LivingEntity owner) {
        BlockPos blockPos = owner.blockPosition();
        for (int i = 0; i < MAX_TELEPORT_ATTEMPTS_TIMES; ++i) {
            int x = this.randomIntInclusive(this.getRandom(), -3, 3);
            int y = this.randomIntInclusive(this.getRandom(), -1, 1);
            int z = this.randomIntInclusive(this.getRandom(), -3, 3);
            if (this.maybeTeleportTo(owner, blockPos.getX() + x, blockPos.getY() + y, blockPos.getZ() + z)) {
                return true;
            }
        }
        return false;
    }

    private boolean maybeTeleportTo(LivingEntity owner, int x, int y, int z) {
        if (this.teleportTooClosed(owner, x, z)) {
            return false;
        } else if (!this.canTeleportTo(new BlockPos(x, y, z))) {
            return false;
        } else {
            this.moveTo(x + 0.5, y, z + 0.5, this.getYRot(), this.getXRot());
            this.getNavigation().stop();
            Brain<?> brain = this.getBrain();
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            brain.eraseMemory(MemoryModuleType.PATH);
            return true;
        }
    }

    private boolean teleportTooClosed(LivingEntity owner, int x, int z) {
        return Math.abs(x - owner.getX()) < 2 && Math.abs(z - owner.getZ()) < 2;
    }

    private boolean canTeleportTo(BlockPos pos) {
        BlockPathTypes pathNodeType = WalkNodeEvaluator.getBlockPathTypeStatic(this.level, pos.mutable());
        if (pathNodeType == BlockPathTypes.WALKABLE || pathNodeType == BlockPathTypes.WATER) {
            BlockPos blockPos = pos.subtract(this.blockPosition());
            return this.level.noCollision(this, this.getBoundingBox().move(blockPos));
        }
        return false;
    }

    private int randomIntInclusive(java.util.Random random, int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    /**
     * 主人手持的诱惑物（上游默认蛋糕，可被配置 / 标签扩展；配置系统尚未移植）。
     */
    public Predicate<ItemStack> getTemptationItem() {
        return matchItemIds(com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig.temptationItem()
                .equals("") ? java.util.List.of() :
                java.util.List.of(com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig.temptationItem()));
    }

    /**
     * 副手是否拿着可用的盾牌（上游 {@code EntityMaid#canUseShield}）。
     *
     * <p>与上游的差异：上游用 {@code ToolActions.SHIELD_BLOCK} 判定并检查物品冷却，
     * 这两者在 1.18.2 里分别是 Forge 扩展接口与 {@code Player} 专属方法。
     * 这里改为判断 {@link ShieldItem}（1.18.2 vanilla 只有盾牌能做这个动作）。</p>
     */
    public boolean canUseShield() {
        ItemStack offhandItem = this.getOffhandItem();
        return offhandItem.getItem() instanceof ShieldItem;
    }

    /**
     * 是否处于「举弓 / 举盾」姿态（上游由 {@code RangedAttackMob#setSwingingArms} 提供，
     * 1.18.2 的该接口没有这个方法兜底语义方法，因此这里自己用一个同步位实现，供动画层使用）。
     */
    public boolean isSwingingArms() {
        return this.entityData.get(DATA_ARM_RISE);
    }

    public void setSwingingArms(boolean swingingArms) {
        this.entityData.set(DATA_ARM_RISE, swingingArms);
    }

    /**
     * 远程攻击：委托给当前 Task（上游 {@code EntityMaid#performRangedAttack}）。
     *
     * <p>这里<b>刻意不调用 {@code swing()} </b>：上游 1.18.2 的
     * {@code MaidShootTargetTask} / {@code TaskDanmakuAttack} / {@code TaskCrossbowAttack}
     * 都没有这一步，原版骷髅的 {@code RangedBowAttackGoal} 也没有 ——
     * 拉弓 / 持御币期间保持双手静止的持械姿态，松手后直接回到 idle，不做收势挥击。</p>
     */
    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        IMaidTask maidTask = this.getTask();
        if (maidTask instanceof IRangedAttackTask rangedAttackTask) {
            rangedAttackTask.performRangedAttack(this, target, distanceFactor);
        }
    }

    // ---------------------------------------------------------------- 弩（CrossbowAttackMob）

    @Override
    public void setChargingCrossbow(boolean isCharging) {
        this.entityData.set(DATA_IS_CHARGING_CROSSBOW, isCharging);
    }

    /**
     * 4 参版本：先按好感度加成伤害，再走 5 参版本发射。
     *
     * <p>烟花火箭的伤害很特殊，所以只对普通箭实体做加成，与上游一致。</p>
     */
    @Override
    public void shootCrossbowProjectile(LivingEntity target, ItemStack crossbow, Projectile projectileEntity,
                                        float projectileAngle) {
        if (projectileEntity instanceof AbstractArrow arrow) {
            arrow.setBaseDamage(arrow.getBaseDamage() * this.getFavorabilityDamageMultiplier());
        }
        this.shootCrossbowProjectile(this, target, projectileEntity, projectileAngle, 1.6F);
    }

    /**
     * 5 参版本：改掉原版默认实现，让女仆能实现超远距离打击。
     *
 * <p>原版是按固定角度 / 固定速度射，射程被限制在 16 格内；这里依据距离调整
     * 速度与不准确度，并让弹体忽略重力，因此百发百中。</p>
     */
    @Override
    public void shootCrossbowProjectile(LivingEntity shooter, LivingEntity target, Projectile projectile,
                                        float angle, float velocityIn) {
        double x = target.getX() - shooter.getX();
        double y = target.getEyeY() - shooter.getEyeY();
        double z = target.getZ() - shooter.getZ();
        float distance = shooter.distanceTo(target);
        float velocity = Mth.clamp(distance / 10f, velocityIn, 3.2f);
        float inaccuracy = 1 - Mth.clamp(distance / 100f, 0, 0.9f);
        projectile.setNoGravity(true);
        projectile.shoot(x, y, z, velocity, inaccuracy);
        shooter.playSound(SoundEvents.CROSSBOW_SHOOT, 1.0F,
                1.0F / (shooter.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    @Override
    public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

    /**
     * 弩装填弹药时，原版 {@code CrossbowItem#tryLoadProjectiles} 会从这里取物品。
     *
     * <p>与上游一致：烟花只认副手（优先取副手），箭矢则从「双手 + 背包」里找。</p>
     */
    @Override
    public ItemStack getProjectile(ItemStack weaponStack) {
        if (this.getOffhandItem().getItem() instanceof FireworkRocketItem) {
            return this.getOffhandItem();
        }
        if (!(this.getMainHandItem().getItem() instanceof ProjectileWeaponItem weaponItem)) {
            return ItemStack.EMPTY;
        }
        CombinedInvWrapper handler = this.getAvailableInv(true);
        int slot = com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil
                .findStackSlot(handler, weaponItem.getAllSupportedProjectiles());
        return slot < 0 ? ItemStack.EMPTY : handler.getStackInSlot(slot);
    }

    /**
     * 原版 {@code CrossbowAttackMob} 要求实现 {@code getTarget()}.
     *
     * <p>女仆的攻击目标是 Brain 里的 {@code ATTACK_TARGET} 记忆，而不是
     * {@code Mob#target} 字段（Brain 的 {@code StartAttacking} 只写记忆）。
     * 上游同样这样重写，否则 {@code CrossbowAttack} 拿不到目标。</p>
     */
    @Override
    @Nullable
    public LivingEntity getTarget() {
        Brain<?> brain = super.getBrain();
        if (brain == null) {
            return null;
        }
        return brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    /**
     * 攻击伤害按好感度缩放（上游注释：伤害和好感度挂钩）。
     */
    private float getFavorabilityDamageMultiplier() {
        var attackDamage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        double attackValue = attackDamage == null ? 2.0 : attackDamage.getBaseValue();
        return (float) (attackValue / 2.0f);
    }

    /**
     * 真正的「吸取」入口（上游 {@code EntityMaid#pushEntities} 的重写）。
     *
     * <p><b>为什么写在 {@code pushEntities} 里：</b>这个方法每 tick 由
     * {@code Entity#baseTick} 之前调用一次，且原版就在里面做「把自己附近的实体推开」，
     * 是「贴身范围内每 tick 扫一遍」最自然的落点。上游正是借用它做拾取：</p>
     *
     * <pre>
     * if (isPickup() &amp;&amp; isTame()) {
     *     getEntities(this, getBoundingBox().inflate(0.5, 0, 0.5), this::canPickup)
     *         .forEach(e -&gt; 按类型分发到 pickupItem / pickupXPOrb / pickupPowerPoint / pickupArrow);
     * }
     * </pre>
     *
     * <p><b>少了它会怎样：</b>{@code MaidPickupEntitiesTask} 只负责「走过去」
     * （它只写 {@code WALK_TARGET}），真正把东西收进背包的是这里。
     * 缺了这一段的表现就是：拾取开着的、女仆也确实会走到物品和经验球旁边，
     * 但永远吸不进去 —— 因为没有任何代码调用 {@code pickupItem} / {@code pickupXPOrb}。</p>
     *
     * <p>两个细节：</p>
     * <ul>
     *     <li>搜索盒是 {@code inflate(0.5, 0, 0.5)} —— 只有水平方向扩了半格，
     *         垂直方向不扩，否则站在物品上方一层时就会隔空吸东西；</li>
     *     <li>谓词用 {@code canPickup(Entity)}（即 {@code checkInWater = false}）：
     *         这里的实体已经在贴身范围内，「在水里」不再是拒绝理由，
     *         否则掉在水洼里的物品永远捡不起来。</li>
     * </ul>
     */
    @Override
    protected void pushEntities() {
        // 被抱着时不推任何实体（包括玩家）。只在没被抱时走原版逻辑。
        if (!this.isCarried()) {
            super.pushEntities();
        }
        // 只有拾取模式开启、且已驯服，才可以捡东西
        if (!this.isPickup() || !this.isTame()) {
            return;
        }
        List<Entity> entityList = this.level.getEntities(this,
                this.getBoundingBox().inflate(0.5D, 0.0D, 0.5D), this::canPickup);
        if (entityList.isEmpty() || !this.isAlive()) {
            return;
        }
        for (Entity entityPickup : entityList) {
            if (entityPickup instanceof ItemEntity itemEntity) {
                this.pickupItem(itemEntity, false);
            }
            if (entityPickup instanceof net.minecraft.world.entity.ExperienceOrb orb) {
                this.pickupXPOrb(orb);
            }
            if (entityPickup instanceof com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint point) {
                this.pickupPowerPoint(point);
            }
            if (entityPickup instanceof AbstractArrow arrow) {
                this.pickupArrow(arrow, false);
            }
        }
    }


    /**
     * 拾取判定（上游 {@code EntityMaid#canPickup}）。
     *
 * <p>需要同时满足：拾取开关打开、不在水里（可选检查）、拾取类型允许该实体类别、
     * 且装得下。物品用 {@code simulate = true} 试塞，因此不会真的移动物品。</p>
     */
    public boolean canPickup(Entity pickupEntity, boolean checkInWater) {
        if (!this.isPickup()) {
            return false;
        }
        if (checkInWater && pickupEntity.isInWater()) {
            return false;
        }
        PickType pickupType = this.getPickupType();
        if (pickupType.canPickItem() && pickupEntity instanceof ItemEntity itemEntity) {
            return this.pickupItem(itemEntity, true);
        }
        if (pickupType.canPickItem() && pickupEntity instanceof AbstractArrow arrow) {
            return this.pickupArrow(arrow, true);
        }
        if (pickupType.canPickXp() && pickupEntity instanceof net.minecraft.world.entity.ExperienceOrb) {
            // 经验球只要类型允许就能捡，不需要预先判断容量
            return true;
        }
        return pickupType.canPickXp()
                && pickupEntity instanceof com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint;
    }

    public boolean canPickup(Entity pickupEntity) {
        return this.canPickup(pickupEntity, false);
    }

    /**
     * 拾取经验球（上游 {@code EntityMaid#pickupXPOrb}）。
     *
     * <p>与上游一致的两点：</p>
     * <ol>
     *     <li>{@code tickCount > 2}：刚生成的经验球有两 tick 不属于任何人的保护期；</li>
     *     <li>先给带「经验修补」的装备修补耐久，剩下的才计入女仆经验。</li>
     * </ol>
     *
     * <p>修补逻辑按原版 {@code ExperienceOrb#repairPlayerItems} 的比率：
     * 1 点经验 = 2 点耐久，被用掉的那部分经验减半计入消耗。</p>
     */
    public void pickupXPOrb(net.minecraft.world.entity.ExperienceOrb orb) {
        if (this.level.isClientSide || !orb.isAlive() || orb.tickCount <= 2) {
            return;
        }
        this.take(orb, 1);
        this.playPickupSound();

        int value = orb.getValue();
        value = this.repairMendingItems(value);
        if (value > 0) {
            this.setExperience(this.getExperience() + value);
        }
        orb.discard();
    }

    /**
     * 拾取 P 点（上游 {@code EntityMaid#pickupPowerPoint}）。
     *
     * <p>P 点按 4P = 1XP 折算后走同一套经验修补流程。</p>
     */
    public void pickupPowerPoint(com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint point) {
        if (this.level.isClientSide || !point.isAlive() || point.throwTime > 0) {
            return;
        }
        point.take(this, 1);
        this.playPickupSound();

        int value = com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint
                .transPowerValueToXpValue(point.getValue());
        value = this.repairMendingItems(value);
        if (value > 0) {
            this.setExperience(this.getExperience() + value);
        }
        point.discard();
    }

    /**
     * 用经验修补背包 / 护甲 / 双手里带「经验修补」的物品。
     *
     * <p>对应上游 {@code getRandomItemWithMendingEnchantments} + 原版的修补比率。
     * 每 1 点经验换 2 点耐久，被消耗掉的那部分经验按一半计回消耗量
     * （原版 {@code durabilityToXp(d) = d / 2}）。</p>
     *
     * @return 修补之后剩下的经验
     */
    private int repairMendingItems(int xpValue) {
        ItemStack target = this.getRandomItemWithMending();
        if (target.isEmpty() || !target.isDamaged()) {
            return xpValue;
        }
        int repair = Math.min(xpValue * 2, target.getDamageValue());
        target.setDamageValue(target.getDamageValue() - repair);
        return xpValue - repair / 2;
    }

    /**
 * 从「护甲 + 双手 + 背包」里随机挑一件带经验修补且已损耗的物品（上游同名方法）。
     *
     * <p>随机挑选而非固定顺序，是为了让多件装备均摊修补，避免永远只修第一件。</p>
     */
    private ItemStack getRandomItemWithMending() {
        List<ItemStack> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < this.maidInv.getSlots(); i++) {
            ItemStack stack = this.maidInv.getStackInSlot(i);
            if (this.isMendingDamaged(stack)) {
                candidates.add(stack);
            }
        }
        for (ItemStack stack : this.getHandSlots()) {
            if (this.isMendingDamaged(stack)) {
                candidates.add(stack);
            }
        }
        for (ItemStack stack : this.getArmorSlots()) {
            if (this.isMendingDamaged(stack)) {
                candidates.add(stack);
            }
        }
        if (candidates.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return candidates.get(this.getRandom().nextInt(candidates.size()));
    }

    private boolean isMendingDamaged(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamaged()
                && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                        net.minecraft.world.item.enchantment.Enchantments.MENDING, stack) > 0;
    }

    /**
     * 拾取箭矢（上游 {@code EntityMaid#pickupArrow}）。
     *
     * <p>与上游一致的条件：必须是 {@code ALLOWED}（不是无限箭 / 别人的创造箭），
     * 且不在「正在被玩家拾取的抖动期」。</p>
     *
     * <p>与上游的差异：上游直接读 {@code arrow.getPickupItemStackOrigin()}，
     * 那个方法在 1.18.2 不存在；这里改用 {@code AbstractArrow#getPickedResult}
     * 之外最稳的办法 —— 药箭用 {@code ARROW} + 药水效果重建，普通箭直接用
     * {@code ARROW}（原版也是这么做的）。</p>
     */
    public boolean pickupArrow(AbstractArrow arrow, boolean simulate) {
        if (this.level.isClientSide || !arrow.isAlive() || arrow.shakeTime > 0) {
            return false;
        }
        if (arrow.pickup != net.minecraft.world.entity.projectile.AbstractArrow.Pickup.ALLOWED) {
            return false;
        }
        ItemStack stack = this.getArrowFromEntity(arrow);
        if (stack.isEmpty() || !canInsertItem(stack)) {
            return false;
        }
        ItemStack remaining = ItemHandlerHelper.insertItemStacked(this.getAvailableInv(false), stack, simulate);
        if (remaining.getCount() == stack.getCount()) {
            return false;
        }
        if (!simulate) {
            this.take(arrow, 1);
            this.playPickupSound();
            arrow.discard();
        }
        return true;
    }

    /**
     * 把箭实体还原成物品堆。
     *
     * <p>药箭在 1.18.2 里没有「保存药水效果的物品」这种直接映射，
     * 但 {@code AbstractArrow#getColor()} 只在药箭上非 -1，因此可以据此判断；
     * 真正的药水类型信息不保留（原版女仆也没有这层需求）。</p>
     */
    private ItemStack getArrowFromEntity(AbstractArrow arrow) {
        if (arrow instanceof net.minecraft.world.entity.projectile.Arrow) {
            return new ItemStack(Items.ARROW);
        }
        if (arrow instanceof net.minecraft.world.entity.projectile.SpectralArrow) {
            return new ItemStack(Items.SPECTRAL_ARROW);
        }
        return ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------- inventory / backpack

    public ItemStackHandler getMaidInv() {
        return this.maidInv;
    }

    /**
     * Maid bauble inventory (upstream EntityMaid#getMaidBauble).
     */
    public com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler getMaidBauble() {
        return this.maidBauble;
    }

    public IMaidBackpack getMaidBackpackType() {
        // Same as upstream: an unknown id (e.g. the type was removed from the registry)
        // degrades to the empty backpack instead of throwing.
        ResourceLocation id = parseId(this.entityData.get(DATA_BACKPACK_TYPE));
        if (id == null) {
            return BackpackManager.getEmptyBackpack();
        }
        return BackpackManager.findBackpack(id).orElseGet(BackpackManager::getEmptyBackpack);
    }

    public void setMaidBackpackType(IMaidBackpack backpack) {
        IMaidBackpack value = backpack == null ? BackpackManager.getEmptyBackpack() : backpack;
        this.entityData.set(DATA_BACKPACK_TYPE, value.getId().toString());
    }

    /**
     * Backpack swap cooldown (upstream {@code backpackHasDelay}).
     */
    public boolean backpackHasDelay() {
        return this.backpackDelay > 0;
    }

    /**
     * Start the 20 tick backpack swap cooldown.
     *
     * <p>Interactions are ignored while it runs, so a single right click - which vanilla
     * dispatches once per hand - cannot put a backpack on and immediately take it off.</p>
     */
    public void setBackpackDelay() {
        this.backpackDelay = 20;
    }

    /**
     * Available inventory: {@code handsFirst} decides whether the hands are searched
     * before or after the backpack (upstream method of the same name).
     */
    public CombinedInvWrapper getAvailableInv(boolean handsFirst) {
        RangedWrapper backpackInv = this.getAvailableBackpackInv();
        return handsFirst
                ? new CombinedInvWrapper(this.handsInvWrapper, backpackInv)
                : new CombinedInvWrapper(backpackInv, this.handsInvWrapper);
    }

    /**
     * Backpack part of the inventory: the first N slots, where N depends on the
     * current backpack type.
     */
    public RangedWrapper getAvailableBackpackInv() {
        return new RangedWrapper(this.maidInv, 0, this.getMaidBackpackType().getAvailableMaxContainerIndex());
    }

    public EntityHandsInvWrapper getHandsInvWrapper() {
        return this.handsInvWrapper;
    }

    public EntityArmorInvWrapper getArmorInvWrapper() {
        return this.armorInvWrapper;
    }

    // ---------------------------------------------------------------- 用餐

    /**
     * 「临时藏起来的物品」栏（1 格，上游 {@code hideInv}）。
     *
     * <p><b>为什么需要它：</b>女仆吃饭时如果两只手都占着，行为会从背包里取一份食物
     * <b>换到手上</b>，原来那只手拿的东西总得有个地方放 —— 直接丢地上显然不对。
     * 上游把原物品存进这个隐藏槽，等 {@link #completeUsingItem()}（吃完那一刻）
     * 再由 {@link #backCurrentHandItemStack()} 还回手上。</p>
     *
     * <p>它<b>不属于</b>玩家能看到的任何界面，只参与「吃饭换手」这一段流程，
     * 但必须写进存档：换手期间区块被卸载，藏起来的那把剑不能凭空消失。</p>
     */
    private static final String MAID_HIDE_INVENTORY_TAG = "MaidHideInventory";
    private final ItemStackHandler hideInv = new ItemStackHandler(1);

    public ItemStackHandler getHideInv() {
        return this.hideInv;
    }

    /**
     * 好感度管理器（每种行为的冷却表 + 等级 + 属性成长）。
     *
     * <p>放在实体自己身上而不是外部 {@code Map<UUID, ...>}：玩家 / 实体对象在区块重载后
     * 会被替换，外部表要么漏清理、要么把旧对象的计数认领给新对象。</p>
     */
    private final com.github.tartaricacid.touhoulittlemaid.entity.favorability.FavorabilityManager favorabilityManager =
            new com.github.tartaricacid.touhoulittlemaid.entity.favorability.FavorabilityManager(this);

    public com.github.tartaricacid.touhoulittlemaid.entity.favorability.FavorabilityManager getFavorabilityManager() {
        return this.favorabilityManager;
    }

    /**
     * 对局记录（五子棋胜场 + 这一局的胜负表情），
     * 对应上游 {@code entity/passive/MaidGameRecordManager}。
     *
     * <p>两个 {@code EntityDataAccessor} 声明在那个类里而不是本文件，
     * 只是为了少改一次这个很长的文件；同步数据的 {@code define}
     * 仍然在 {@code defineSynchedData()} 里完成。</p>
     */
    private final MaidGameRecordManager gameRecordManager = new MaidGameRecordManager(this);

    public MaidGameRecordManager getGameRecordManager() {
        return this.gameRecordManager;
    }

    /**
     * 写入同步数据。
     *
     * <p><b>为什么不能原地修改再写回：</b>反编译确认 1.18.2 的
     * {@code SynchedEntityData#set} 用 {@code ObjectUtils.notEqual(新值, 旧值)} 判定要不要标脏，
     * 而 {@code CompoundTag} 的 {@code equals} 比的是<b>内容</b>。若取出同一个实例、改完再塞回去，
     * 「新值」和「旧值」是同一个对象，内容也就相等 → 不标脏 → 客户端永远收不到更新。</p>
     *
     * <p>上游为此直接操络 {@code DataItem} 绕过判定；1.18.2 的 {@code DataItem} 与
     * {@code getItem} 都是 private，没有等价入口，{@code set} 也没有接受 {@code force} 的重载。
     * 这里改为<b>调用方传副本</b>（见 {@code MaidGameRecordManager#getGameSkill}），
     * 内容差异就能被正确识别。方法名保留与上游一致，便于对照。</p>
     */
    public <T> void forceSyncData(net.minecraft.network.syncher.EntityDataAccessor<T> key, T value, boolean force) {
        this.entityData.set(key, value);
    }

    /**
     * 头顶冒爱心（上游发 {@code SpawnParticleMessage} 让附近客户端播放，这里用原版服务端粒子）。
     *
     * @param amount 粒子数量，会被夹到 1~8，避免一次刷出一大片
     */
    public void spawnHeartParticles(int amount) {
        if (this.level.isClientSide) {
            return;
        }
        if (this.level instanceof ServerLevel serverLevel) {
            int count = Mth.clamp(amount, 1, 8);
            serverLevel.sendParticles(ParticleTypes.HEART,
                    this.getX(), this.getY() + this.getBbHeight() + 0.2D, this.getZ(),
                    count, 0.3D, 0.3D, 0.3D, 0.02D);
        }
    }

    /**
     * 冒出 20 个白色烟雾粒子（上游 {@code EntityMaid#spawnExplosionParticle}）。
     *
     * <p>相机把女仆收进照片、照片再把女仆放出来时都会调它 ——
     * 这是「收纳 / 放出」唯一的视觉反馈，少了它女仆会像凭空消失。</p>
     *
     * <p><b>只在客户端执行</b>（上游同款）：粒子本身是纯表现，
     * 服务端调 {@code Level#addParticle} 什么也不会发生，白白跑一遍循环。
     * 由于相机与照片的逻辑在两端都会走到这一句，客户端能立刻看到效果，
     * 不必等实体同步包。</p>
     *
     * <p>速度给 {@code ±0.01} 的极小值而不是 0：完全静止的粒子在部分光影下
     * 会因为三角面朝向不变而看起来像一团固定的白块。</p>
     */
    public void spawnExplosionParticle() {
        if (this.level.isClientSide) {
            for (int i = 0; i < 20; ++i) {
                float mx = (this.random.nextFloat() - 0.5F) * 0.02F;
                float my = (this.random.nextFloat() - 0.5F) * 0.02F;
                float mz = (this.random.nextFloat() - 0.5F) * 0.02F;
                this.level.addParticle(ParticleTypes.CLOUD,
                        this.getX() + this.random.nextFloat() - 0.5F,
                        this.getY() + this.random.nextFloat() - 0.5F,
                        this.getZ() + this.random.nextFloat() - 0.5F,
                        mx, my, mz);
            }
        }
    }

    /**
     * 把「原本手上拿的东西」暂存进隐藏栏（上游 {@code EntityMaid#memoryHandItemStack}）。
     *
     * <p>若隐藏栏里已经有东西（上一轮换手还没还回去），先把它掉到地上再存新的 ——
     * 与上游一致：宁可掉地上，也不静默吞掉玩家给女仆的装备。</p>
     */
    public void memoryHandItemStack(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return;
        }
        ItemStack hideItemStack = this.hideInv.getStackInSlot(0);
        if (!hideItemStack.isEmpty()) {
            ItemStack extractItem = this.hideInv.extractItem(0, hideItemStack.getCount(), false);
            if (!extractItem.isEmpty()) {
                ItemEntity itemEntity = new ItemEntity(this.level, this.getX(), this.getY() + 0.5D, this.getZ(), extractItem);
                this.level.addFreshEntity(itemEntity);
            }
        }
        ItemHandlerHelper.insertItemStacked(this.hideInv, itemStack, false);
    }

    /**
     * 把隐藏栏的物品还回手上（上游 {@code EntityMaid#backCurrentHandItemStack}）。
     *
     * <p>顺序很重要：先把副手上「吃剩下的食物 / 返还的容器」塞回背包，副手腾空之后
     * 再把藏起来的物品放回副手。反过来的话会把藏起来的剑顶掉。</p>
     */
    private void backCurrentHandItemStack() {
        ItemStack offhandItem = this.getItemInHand(InteractionHand.OFF_HAND);
        if (!offhandItem.isEmpty()) {
            ItemStack stack = ItemHandlerHelper.insertItemStacked(this.getAvailableBackpackInv(), offhandItem.copy(), false);
            if (!stack.isEmpty()) {
                ItemEntity itemEntity = new ItemEntity(this.level, this.getX(), this.getY() + 0.5D, this.getZ(), stack);
                this.level.addFreshEntity(itemEntity);
            }
        }
        ItemStack output = this.hideInv.extractItem(0, this.hideInv.getStackInSlot(0).getCount(), false);
        if (!output.isEmpty()) {
            this.setItemInHand(InteractionHand.OFF_HAND, output);
        }
    }

    /**
     * 吃完那一刻的收尾（上游 {@code EntityMaid#completeUsingItem}）。
     *
     * <p>原版 {@code completeUsingItem} 负责真正的「消耗物品」：
     * {@code finishUsingItem} → {@code eat}（数量 -1、加药水效果、回血）。
     * 吃完之后手上那只手已经空出来，正好把之前藏的物品还回去。</p>
     */
    @Override
    protected void completeUsingItem() {
        super.completeUsingItem();
        this.backCurrentHandItemStack();
    }

    /**
     * 吃东西（上游 {@code EntityMaid#eat} 的重写）。
     *
     * <p>上游在这里发 {@code MaidAfterEatEvent}，由 {@code DefaultEatenEvent} 处理
     * 「容器返还」：例如迷之炖菜吃完要还一个碗，而配置项
     * {@code MaidEatenReturnContainerList} 允许给任意食物配一个返还物。
     * Fabric 侧没有事件总线，这里直接做同一件事。</p>
     *
     * <p>解析顺序与上游一致：<b>先看物品自带的 {@code getCraftingRemainingItem()}，
     * 为空才查配置表</b>。</p>
     */
    @Override
    public ItemStack eat(Level level, ItemStack food) {
        ItemStack afterEat = super.eat(level, food);
        this.handleEatenContainer(afterEat);
        return afterEat;
    }

    /**
     * 是否被雷劈过（上游用 Forge 的 {@code Entity#isStruckByLightning}，
     * 1.18.2 vanilla 没有这个方法，因此自己记一个位：在 {@link #thunderHit} 里置真）。
     */
    private boolean struckByLightning = false;

    public boolean isStruckByLightning() {
        return this.struckByLightning;
    }

    @Override
    public void thunderHit(ServerLevel level, net.minecraft.world.entity.LightningBolt lightning) {
        super.thunderHit(level, lightning);
        this.struckByLightning = true;
    }


    private void handleEatenContainer(ItemStack afterEat) {
        if (afterEat.isEmpty() || this.level.isClientSide) {
            return;
        }
        // 1.18.2 vanilla 只有 Item#getCraftingRemainingItem()（Forge 才给 ItemStack 加了
        // getContainerItem()），所以这里先问 has 再取，避免拿到空气。
        net.minecraft.world.item.Item afterItem = afterEat.getItem();
        ItemStack container = afterItem.hasCraftingRemainingItem()
                ? new ItemStack(afterItem.getCraftingRemainingItem()) : ItemStack.EMPTY;

        if (container.isEmpty()) {
            String itemId = com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig
                    .getItemId(afterEat.getItem());
            for (List<String> entry : com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig
                    .eatenReturnContainerList()) {
                if (entry.size() >= 2 && entry.get(0).equals(itemId)) {
                    ResourceLocation id = parseId(entry.get(1));
                    if (id != null) {
                        net.minecraft.world.item.Item item = net.minecraft.core.Registry.ITEM.get(id);
                        if (item != Items.AIR) {
                            container = new ItemStack(item);
                        }
                    }
                    break;
                }
            }
        }
        if (container.isEmpty()) {
            return;
        }
        ItemStack leftover = ItemHandlerHelper.insertItemStacked(this.getAvailableInv(false), container, false);
        if (!leftover.isEmpty()) {
            ItemEntity itemEntity = new ItemEntity(this.level, this.getX(), this.getY(), this.getZ(), leftover);
            this.level.addFreshEntity(itemEntity);
        }
    }


    /**
     * 是否为该玩家驯服的女仆（UUID 直接比较，不依赖玩家实体是否在场）。
     */
    public boolean isOwnedByPlayer(Player player) {
        // 只认 Owner UUID，不再额外要求 isTame()。
        //
        // isTame() 读的是 TamableAnimal 的【同步标志位】，有很多环节会把它清掉
        // （落水 / 重新加载 / 旧存档里 Tame 位与 Owner 不一致）；
        // 而 Owner UUID 是持久化字段，语义上「有主」就足以判定归属。
        // 早年实现要求 isTame()，结果出现「旧女仆完全没反应、新驯的却正常」。
        java.util.UUID ownerUUID = this.getOwnerUUID();
        return ownerUUID != null && ownerUUID.equals(player.getUUID());
    }

    /**
     * 客户端专用的「当前状态效果」快照（上游 {@code EntityMaid#effects}）。
     *
     * <p><b>这个字段只在客户端有意义。</b>原版不会把「非玩家实体」的状态效果同步到客户端
     * —— {@code ClientPacketListener#handleUpdateMobEffect} 的接收方写死为
     * {@code this.minecraft.player}，别的实体挂什么效果客户端根本收不到。
     * 所以客户端这份列表只能靠 {@code MaidEffectPacket} 主动向服务端要；
     * 服务端侧它永远是空集合，没有任何代码读取。</p>
     *
     * <p><b>为什么挂在实体上而不是界面上：</b>三个页签（主界面 / 任务设置 / 女仆配置）
     * 是三个独立的 Screen，切页签时旧实例会被销毁重建；数据挂在实体上，
     * 切页签的瞬间不会闪一下空白。上游同样挂在实体上。</p>
     */
    private final List<MaidEffectData> effects = new java.util.ArrayList<>();

    public List<MaidEffectData> getEffects() {
        return this.effects;
    }

    /**
     * 用服务端回传的数据整体替换本地列表。
     *
     * <p>是「替换」而不是「合并」：服务端回传的就是该时刻的完整快照，
     * 合并只会留下已经到期、但这次没被回传的残留项。</p>
     */
    /**
     * Tells nearby clients that a piece of gear broke (upstream #sendItemBreakMessage).
     * LivingEntity#breakItem is client-only, so the server can only notify.
     */
    public void sendItemBreakMessage(ItemStack stack) {
        if (!this.level.isClientSide) {
            com.github.tartaricacid.touhoulittlemaid.network.ItemBreakPacket.sendNearby(this, stack);
        }
    }

    /**
     * Bubble burst (upstream EntityMaid#spawnBubbleParticle). Client side only.
     */
    public void spawnBubbleParticle() {
        if (this.level.isClientSide) {
            for (int i = 0; i < 8; ++i) {
                double offsetX = 2 * this.random.nextDouble() - 1;
                double offsetY = this.random.nextDouble() / 2;
                double offsetZ = 2 * this.random.nextDouble() - 1;
                this.level.addParticle(ParticleTypes.BUBBLE,
                        this.getX() + offsetX, this.getY() + offsetY, this.getZ() + offsetZ, 0, 0.1, 0);
            }
        }
    }

    /**
     * Hearts (upstream EntityMaid#spawnHeartParticle).
     * Note the split: spawnHeartParticles(int) is the server-side broadcast form used by
     * interactions, this one is the client-side local form driven by the particle packet.
     */
    public void spawnHeartParticle() {
        if (this.level.isClientSide) {
            for (int i = 0; i < 8; ++i) {
                double offsetX = this.random.nextGaussian() * 0.02;
                double offsetY = this.random.nextGaussian() * 0.02;
                double offsetZ = this.random.nextGaussian() * 0.02;
                this.level.addParticle(ParticleTypes.HEART,
                        this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0),
                        offsetX, offsetY, offsetZ);
            }
        }
    }

    /**
     * Healing sparkles (upstream EntityMaid#spawnRestoreHealthParticle).
     */
    public void spawnRestoreHealthParticle(int particleCount) {
        if (this.level.isClientSide) {
            for (int i = 0; i < particleCount; ++i) {
                double xRandom = this.random.nextGaussian() * 0.02;
                double yRandom = this.random.nextGaussian() * 0.02;
                double zRandom = this.random.nextGaussian() * 0.02;
                this.level.addParticle(ParticleTypes.ENTITY_EFFECT,
                        this.getX() + (this.random.nextFloat() * this.getBbWidth() * 2.0F) - this.getBbWidth() - xRandom * 10.0,
                        this.getY() + (this.random.nextFloat() * this.getBbHeight()) - yRandom * 10.0,
                        this.getZ() + (this.random.nextFloat() * this.getBbWidth() * 2.0F) - this.getBbWidth() - zRandom * 10.0,
                        0.9, 0.1, 0.1);
            }
        }
    }

    /**
     * Ranking-up burst (upstream EntityMaid#spawnRankUpParticle).
     * Deviation: upstream also shows a client title through Minecraft#gui; this class is
     * shared code, so referencing Minecraft here would drag client classes into the server
     * jar. The totem burst and the bell sound are kept.
     */
    public void spawnRankUpParticle() {
        if (this.level.isClientSide) {
            this.level.addParticle(ParticleTypes.TOTEM_OF_UNDYING,
                    this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), 0, 0.1, 0);
            this.level.playLocalSound(this.getX(), this.getY(), this.getZ(),
                    SoundEvents.BELL_BLOCK, this.getSoundSource(), 1.0F, 1.0F, false);
        }
    }

    public void setEffects(List<MaidEffectData> effects) {
        this.effects.clear();
        this.effects.addAll(effects);
    }

    /**
     * 上游 {@code EntityMaid#tameMaid} 的移植：数量上限 + 驯服物品 / NTR 物品判定。
     */
    private InteractionResult tameMaid(ItemStack stack, Player player) {
        if (!this.level.isClientSide) {
            boolean canAdd = player.isCreative() || this.canAddMaid(player);
            if (!canAdd) {
                player.displayClientMessage(
                        new TranslatableComponent("message.touhou_little_maid.owner_maid_num.can_not_add",
                                this.countOwnedMaid(player), MaidNumCapability.DEFAULT_MAX_NUM), true);
                return InteractionResult.PASS;
            }
            boolean isNormal = !this.isTame() && this.getTamedItem().test(stack);
            boolean isNtr = this.getNtrItem().test(stack);
            if (isNormal || isNtr) {
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
                this.tame(player);
                // 清掉寻路与敌对目标（上游还会 eraseMemory(ATTACK_TARGET)，这里行为已移植，一起清）
                this.getNavigation().stop();
                this.setTarget(null);
                this.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                this.level.broadcastEntityEvent(this, (byte) 7);
                this.playMaidVoice(InitSounds.MAID_TAMED, 1.0F, 1.0F);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    /**
     * 驯服物品判定（上游 {@code getTamedItem()}，默认蛋糕）。
     */
    public Predicate<ItemStack> getTamedItem() {
        return matchItemIds(java.util.List.of(
                com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig.tamedItem()));
    }

    /**
     * NTR 物品判定（上游 {@code getNtrItem()}，默认结构空位）。
     */
    public static Predicate<ItemStack> getNtrItem() {
        return matchItemIds(java.util.List.of(
                com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig.ntrItem()));
    }

    /**
     * 统计该玩家当前拥有（已驯服且归属自己）的女仆数量。
     *
     * <p>上游用 capability 维护计数；Fabric 侧改为按需统计，语义一致且无需持久化。</p>
     */
    public int countOwnedMaid(Player player) {
        List<EntityMaid> maids = this.level.getEntitiesOfClass(EntityMaid.class,
                new AABB(this.blockPosition()).inflate(256), maid -> maid.isTame() && maid.isOwnedBy(player));
        return maids.size();
    }

    private boolean canAddMaid(Player player) {
        return new MaidNumCapability().canAdd(this.countOwnedMaid(player));
    }

    // ---------------------------------------------------------------- 坐姿

    /**
 * 玩家交互入口：同时写「坐姿位」与「指令位」（上游同款实现）。
     *
     * <p>「坐姿位」是同步的 vanilla 数据位，客户端动画与碰撞 / 行为都读它；
     * 「指令位」{@code orderedToSit} 只用于持久化与 AI 判定，不同步。</p>
     */
    @Override
    public void setInSittingPose(boolean inSittingPose) {
        super.setInSittingPose(inSittingPose);
        this.setOrderedToSit(inSittingPose);
        if (inSittingPose) {
            this.getNavigation().stop();
        }
    }

    /**
     * 只写「坐姿位」，不动「指令位」。
     *
     * <p>坐姿动画、{@code Mob} 的坐姿相关逻辑都只认这一个位，所以它是全模组唯一权威值；
     * 指令位则只受玩家交互 / 存档影响，不会被 AI 顺手清掉。</p>
     */
    public void setSittingPoseOnly(boolean sittingPose) {
        super.setInSittingPose(sittingPose);
    }

    /**
     * 动画层看到的坐姿。
     *
     * <p>直接读 vanilla 的同步坐姿位（与上游 {@code EntityMaid#isMaidInSittingPose} 一致）。
     * 曾经用额外的 {@code DATA_SITTING_POSE} 字段做「独立数据源」，结果两份数据写入时机
     * 不同就会出现「实体已经站起来、模型还坐着并跟着人平移」；现在只有一份数据，
     * 动画与实体行为不可能各说各话。</p>
     */
    @Override
    public boolean isMaidInSittingPose() {
        return super.isInSittingPose();
    }

    /**
     * 服务端真实的坐姿位（与 {@link #isMaidInSittingPose()} 同源），保留给诊断日志使用。
     */
    public boolean isVanillaSittingPose() {
        return super.isInSittingPose();
    }

    // ---------------------------------------------------------------- 同步数据读写

    public String getModelId() {
        return this.entityData.get(DATA_MODEL_ID);
    }

    @Override
    public String getMaidModelId() {
        return this.getModelId();
    }

    public void setModelId(String modelId) {
        this.entityData.set(DATA_MODEL_ID, modelId);
    }

    public int getFavorability() {
        return this.entityData.get(DATA_FAVORABILITY);
    }

    public void setFavorability(int value) {
        this.entityData.set(DATA_FAVORABILITY, value);
    }

    public boolean isPickup() {
        return this.entityData.get(DATA_PICKUP);
    }

    public void setPickup(boolean pickup) {
        this.entityData.set(DATA_PICKUP, pickup);
    }

    /**
     * Current pickup type (upstream: {@code configManager.getPickupType()}).
     *
     * <p>The id is stored as a string, and an unparsable value falls back to
     * {@link PickType#ALL} instead of throwing: this value goes into the save file,
     * so a corrupted entry must never disable pickup for the whole maid.</p>
     */
    public PickType getPickupType() {
        PickType type = PickType.byId(this.entityData.get(DATA_PICKUP_TYPE));
        return type == null ? PickType.ALL : type;
    }

    public void setPickupType(PickType type) {
        this.entityData.set(DATA_PICKUP_TYPE, (type == null ? PickType.ALL : type).getId());
    }

    /**
     * 是否处于「乞求」姿态（手持蛋糕等食物时的撒娇动画）。
     *
     * <p>由 Brain 的 {@code MaidBegTask} 在服务端写、客户端动画读，因此必须是同步数据。</p>
     */
    @Override
    public boolean isBegging() {
        return this.entityData.get(DATA_BEGGING);
    }

    public void setBegging(boolean begging) {
        this.entityData.set(DATA_BEGGING, begging);
    }

    // ---------------------------------------------------------------- 基础行为

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.CAKE);
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return this.isOwnedBy(player) && super.canBeLeashed(player);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    /**
     * 眼睛高度（上游 {@code EntityMaid#getStandingEyeHeight}）。
     *
     * <p>原版默认是 {@code sizeIn.height * 0.85}，上游在「坐姿」时把它降到 {@code 0.65}，
     * 这样坐下以后「看向玩家」的判定点仍然在头部而不是悬在头顶上方。</p>
     *
     * <p>这个值还决定了睡觉时的渲染位置：{@code LivingEntityRenderer} 对
     * {@code Pose.SLEEPING} 做的那一步水平位移是
     * {@code -bedFacing * (getEyeHeight(Pose.STANDING) - 0.1)}，
     * 也就是「把位于眼睛高度的模型枢轴移回床面」。所以这里必须与上游逐字一致，
     * 否则躺下的位置会整体沿床长方向偏掉。</p>
     */
    @Override
    public float getStandingEyeHeight(net.minecraft.world.entity.Pose pose,
                                      net.minecraft.world.entity.EntityDimensions size) {
        return size.height * (isMaidInSittingPose() ? 0.65F : 0.85F);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag dataTag) {
        this.setPersistenceRequired();
        // 刷怪蛋 / 祭坛 / 魂符 / 结构生成时随机外观，与上游一致；带 dataTag 指定 ModelId 时不覆盖。
        // 池子来自 ServerModelPool（服务端读 jar 内所有 assets/<domain>/maid_model.json），
        // 不是写死的几个 —— 见那个类的注释。
        if (dataTag == null || !dataTag.contains("ModelId")) {
            String randomId = com.github.tartaricacid.touhoulittlemaid.entity.info.ServerModelPool
                    .randomId(this.random);
            if (randomId != null) {
                this.setModelId(randomId);
            }
        }
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    @Override
    public void die(DamageSource source) {
        // Upstream wraps super.die in a cancelable MaidDeathEvent: the undying totem and
        // the extra-life elixir resurrect the maid by cancelling it, both at HIGH priority
        // so they run before anything else observes the death.
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent(this, source)).isCanceled()) {
            return;
        }
        super.die(source);
        if (!this.level.isClientSide) {
            this.level.broadcastEntityEvent(this, (byte) 60);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 60) {
            for (int i = 0; i < 20; i++) {
                double x = this.getX() + (this.random.nextDouble() - 0.5D) * 0.8D;
                double y = this.getY() + this.random.nextDouble() * 1.2D;
                double z = this.getZ() + (this.random.nextDouble() - 0.5D) * 0.8D;
                this.level.addParticle(ParticleTypes.CLOUD, x, y, z, 0, 0.02D, 0);
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    // ---------------------------------------------------------------- 声音

    /**
     * 两次「女仆语音」之间的全局最小间隔（tick）。
     *
     * <p>原版 {@code Mob#baseTick} 的触发条件是
     * {@code random.nextInt(1000) < ambientSoundTime++}，而
     * {@code resetAmbientSoundTime()} 会把计数重置为 {@code -getAmbientSoundInterval()}.
     * 于是默认 {@code interval = 80} 时的平均间隔只有约 125 tick（≈6 秒）——
     * 单只女仆就已经偏密，场上三五只时必然叠在一起，听起来像「一直在说话」。</p>
     *
     * <p>上游靠客户端 {@code MaidSoundFreqEvent} 按「每只女仆的声音频率 × 全局频率」概率丢弃
     * 一部分语音，默认全局 100% 时不过滤；它真正压住密度的是 {@code MaidSoundInstance}
     * 那套自定义 SoundEngine 通道，本移植版没有。这里用一个等价且更直接的手段：
     * <b>全局节流</b> —— 任意两只女仆的语音之间至少间隔这么久，
     * 这无论同屏多少只女仆，同一时刻只有一个在说话。</p>
     */
    private static final int MAID_VOICE_MIN_GAP = 60;
    /**
     * 单只女仆两次环境音之间的基准间隔（tick）。
     *
     * <p>配合原版的「计数回爬 + 概率增长」机制，实际平均间隔约为
     * {@code 本值 + 45} tick，即 {@code 240 → 约 14 秒}。取 240 是个折中：
     * 既不会让玩家觉得女仆是哑巴，也不会 6 秒就冒一句话。</p>
     */
    private static final int MAID_AMBIENT_INTERVAL = 240;
    /**
     * 上一次任一女仆播放语音的全局时刻，用于 {@link #MAID_VOICE_MIN_GAP} 节流。
     *
     * <p>桌面端只有一个客户端 / 一个逻辑服务端，静态字段足够；
     * 用 {@code level.getGameTime()} 做时间轴，跨维度时差值可能为负，
     * 此时判定为「不是同一时间轴」直接放行，不会永久卡住。</p>
     */
    private static long lastMaidVoiceTick = Long.MIN_VALUE / 2;

    /**
     * 单只女仆的环境音间隔（原版默认 80，平均约 6 秒一次，太密）。
     */
    @Override
    public int getAmbientSoundInterval() {
        return MAID_AMBIENT_INTERVAL;
    }

    /**
     * 女仆语音是否要真正播出来（上游 {@code MaidSoundFreqEvent} 的等价实现）。
     *
     * <p><b>这是「声音频率调了没反应」的根因所在。</b>上游的过滤是<b>按声音实例</b>做的：</p>
     *
     * <pre>
     * if (event.getSound() instanceof MaidSoundInstance maidSound) {
     *     double f = maid.getConfigManager().getSoundFreq()
     *              * MaidConfig.GLOBAL_MAID_SOUND_FREQUENCY.get() / 100;
     *     if (f &lt; 1 &amp;&amp; Math.random() &gt; f) event.setSound(null);
     * }
     * </pre>
     *
     * <p>也就是说被拦的不只是「自言自语」，而是<b>所有</b>女仆语音 ——
     * 捡到东西的「哎嘿」、被驯服的欢呼、受伤的叫声，全都在内。</p>
     *
     * <p>本移植版最早只在 {@link #playAmbientSound()} 里拦了一道，其余语音照播，
     * 所以把频率拖到 0% 之后仍然听得见「捡到东西」「受伤」的原声，
     * 主观上就是「这个开关没用」。现在把判定收进这一个方法，所有语音都经过它。</p>
     *
     * <p>概率比较用 {@code <}：频率为 0 时 {@code nextDouble()} 必然不满足，
     * 恒定静音；为 1 时直接短路放行，恒定可闻。</p>
     */
    private boolean shouldPlayVoice() {
        double freq = MaidSubConfig.getSoundFreq(this)
                * com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig
                .globalMaidSoundFrequency() / 100.0D;
        return freq >= 1.0D || this.getRandom().nextDouble() < freq;
    }

    /**
     * 播女仆语音（先过 {@link #shouldPlayVoice()} 的频率判定）。
     *
     * <p>只用于「女仆说话」这一类音效；方块声、挤奶声、弩的射击声不走这里 ——
     * 那些是环境 / 交互动静，关掉语音不该把它们一起弄哑。</p>
     */
    private void playMaidVoice(SoundEvent sound, float volume, float pitch) {
        if (sound == null || !this.shouldPlayVoice()) {
            return;
        }
        this.playSound(sound, volume, pitch);
    }

    /**
     * 环境音。
     *
     * <p>前面的全局节流（{@link #MAID_VOICE_MIN_GAP}）管「多久能说一次」，
     * 频率判定管「这次要不要说」，两者是正交的两件事：</p>
     * <ul>
     *     <li>节流是为了同屏多只女仆时不会叠在一起说话；</li>
     *     <li>频率是玩家在配置界面里按只调整的音量意图。</li>
     * </ul>
     */
    @Override
    public void playAmbientSound() {
        long now = this.level.getGameTime();
        long delta = now - lastMaidVoiceTick;
        // delta < 0 说明换了维度（时间轴不同），不要卡住
        if (delta >= 0 && delta < MAID_VOICE_MIN_GAP) {
            return;
        }
        SoundEvent sound = this.getAmbientSound();
        if (sound == null || !this.shouldPlayVoice()) {
            return;
        }
        lastMaidVoiceTick = now;
        this.playSound(sound, this.getSoundVolume(), this.getVoicePitch());
    }


    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        // 与上游一致：环境音由当前 Task 决定（不同工作模式不同语音），
        // 并按时间段 / 天气 / 生物群系挑环境音。
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new com.github.tartaricacid.touhoulittlemaid.api.event.MaidPlaySoundEvent(this)).isCanceled()) {
            return null;
        }
        return this.getTask().getAmbientSound(this);
    }
    /**
     * 受伤语音。
     *
     * <p>返回 {@code null} 就等于「这一声不出」：原版 {@code LivingEntity#playHurtSound}
     * 取到 null 就直接跳过播放。用返回值而不是重写 {@code playHurtSound}，
     * 是因为 {@code Mob#playHurtSound} 里还夹着 {@code resetAmbientSoundTime()}，
     * 那是 private 的，重写就只能连它一起丢掉。</p>
     */
    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        if (!this.shouldPlayVoice()) {
            return null;
        }
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new com.github.tartaricacid.touhoulittlemaid.api.event.MaidPlaySoundEvent(this)).isCanceled()) {
            return null;
        }
        return source.isFire() ? InitSounds.MAID_HURT_FIRE : InitSounds.MAID_HURT;
    }

    /**
     * 死亡语音，同样用返回 {@code null} 来表示静音。
     */
    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new com.github.tartaricacid.touhoulittlemaid.api.event.MaidPlaySoundEvent(this)).isCanceled()) {
            return null;
        }
        return this.shouldPlayVoice() ? InitSounds.MAID_DEATH : null;
    }


    @Override
    public float getVoicePitch() {
        return 1.3F;
    }

    // ---------------------------------------------------------------- NBT

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // 双保险：vanilla 的 TamableAnimal 只在 Owner 非空时写 "Owner"，且读取时
        // 一旦 UUID 解析失败就 setTame(false)。这里额外存一份自有字段，
        // 一旦出现「驯服过期 / 归属丢失 / 无法交互」，读取阶段可以自愈。
        if (this.getOwnerUUID() != null) {
            tag.putUUID("FutoOwner", this.getOwnerUUID());
        }
        tag.putBoolean("FutoTamed", this.isTame());
        tag.putString("ModelId", this.getModelId());
        tag.putInt("Favorability", this.getFavorability());
        tag.putInt(EXPERIENCE_TAG, this.getExperience());
        tag.putBoolean("Pickup", this.isPickup());
        tag.putString("PickupType", this.getPickupType().getId());
        tag.putString("MaidTask", this.getTask().getUid().toString());
        tag.putString("MaidScheduleMode", this.getSchedule().name());
        tag.putBoolean("HomeModeEnable", this.isHomeModeEnable());
        this.schedulePos.save(tag);
        tag.putLong("MaidRestrictCenter", this.getRestrictCenter().asLong());
        tag.putFloat("MaidRestrictRadius", this.getRestrictRadius());
        // 背包类型（上游同名字段；读档时按注册表反查找不到就退化为空背包）
        tag.putString("MaidBackpackType", this.getMaidBackpackType().getId().toString());
        tag.put(MAID_INVENTORY_TAG, this.maidInv.serializeNBT());
        tag.put(MAID_HIDE_INVENTORY_TAG, this.hideInv.serializeNBT());
        tag.put(MAID_BAUBLE_INVENTORY_TAG, this.maidBauble.serializeNBT());
        this.favorabilityManager.save(tag);
        this.gameRecordManager.addAdditionalSaveData(tag);

        MaidSubConfig.save(tag, this);
        this.attackList.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // 女仆永不自然消失：从 NBT 读出的 PersistenceRequired 可能是 false，
        // 这里强制恢复，避免驯服过的女仆在区块重载后被当成普通生物刷掉。
        this.setPersistenceRequired();
        if (this.getOwnerUUID() == null && tag.hasUUID("FutoOwner")) {
            // vanilla 数据丢失 / 解析失败时，用自有字段恢复归属，
            // 否则女仆会变成「无主」—— 潜行右键既不坐下也不驯服，看起来就是交互失效。
            try {
                this.setOwnerUUID(tag.getUUID("FutoOwner"));
                this.setTame(true);
                TouhouLittleMaid.LOGGER.warn("Restored owner of maid from backup tag");
            } catch (Exception e) {
                TouhouLittleMaid.LOGGER.error("Failed to restore maid owner", e);
            }
        }
        if (this.getOwnerUUID() != null && !this.isTame()) {
            // Owner 还在但 Tame 同步位丢失（旧存档 / 异常重载）—— 补回来，否则
            // 依赖 isTame() 的地方（骑乘、驯服判定）会把这个女仆当成野生的。
            this.setTame(true);
        }
        if (tag.contains("ModelId")) {
            this.setModelId(tag.getString("ModelId"));
        }
        this.setFavorability(tag.getInt("Favorability"));
        if (tag.contains(EXPERIENCE_TAG, net.minecraft.nbt.Tag.TAG_INT)) {
            this.setExperience(tag.getInt(EXPERIENCE_TAG));
        }
        this.setPickup(tag.getBoolean("Pickup"));
        if (tag.contains("PickupType", net.minecraft.nbt.Tag.TAG_STRING)) {
            PickType stored = PickType.byId(tag.getString("PickupType"));
            this.setPickupType(stored == null ? PickType.ALL : stored);
        }
        if (tag.contains("MaidTask")) {
            ResourceLocation taskId = parseId(tag.getString("MaidTask"));
            if (taskId != null) {
                TaskManager.findTask(taskId).ifPresent(this::setTask);
            }
        }
        if (tag.contains("MaidScheduleMode")) {
            // 必须走 setSchedule，不能直接写 entityData：
            // 大脑是在 Mob 的构造函数里按「默认 DAY」建好的（Mob#makeBrain 早于读档），
            // 只改同步数据不会重建活动表 —— 于是读档后 GUI 显示「夜班」、
            // 大脑却仍按白班跑（白天照常干活、晚上才去坐着）。
            // 上游 readAdditionalSaveData 里同样是 setSchedule(...)，它内部会 refreshBrain。
            try {
                this.setSchedule(MaidSchedule.valueOf(tag.getString("MaidScheduleMode")));
            } catch (IllegalArgumentException ignored) {
                this.setSchedule(MaidSchedule.DAY);
            }
        }
        this.setHomeModeEnable(tag.getBoolean("HomeModeEnable"));
        if (tag.contains("MaidRestrictCenter")) {
            this.entityData.set(RESTRICT_CENTER, BlockPos.of(tag.getLong("MaidRestrictCenter")));
        }
        if (tag.contains("MaidRestrictRadius")) {
            this.entityData.set(RESTRICT_RADIUS, tag.getFloat("MaidRestrictRadius"));
        }
        // 作息坐标（上游在同位置调用 SchedulePos#load）。
        // 老存档没有 MaidSchedulePos 子标签：SchedulePos#load 什么都不做，字段保持
        // 构造函数里给的「家 = 当前位置」—— 那些女仆本来就没有罗盘坐标，这是正确行为。
        this.schedulePos.load(tag, this);
        if (tag.contains(MAID_INVENTORY_TAG)) {
            this.maidInv.deserializeNBT(tag.getCompound(MAID_INVENTORY_TAG));
        }
        if (tag.contains(MAID_HIDE_INVENTORY_TAG)) {
            this.hideInv.deserializeNBT(tag.getCompound(MAID_HIDE_INVENTORY_TAG));
        }
        if (tag.contains(MAID_BAUBLE_INVENTORY_TAG)) {
            this.maidBauble.deserializeNBT(tag.getCompound(MAID_BAUBLE_INVENTORY_TAG));
        }
        this.favorabilityManager.load(tag);
        this.gameRecordManager.readAdditionalSaveData(tag);
        // 好感度决定最大生命与攻击力，读档后必须按当前好感度重建一次属性，
        // 否则会出现「好感度 300 但生命還是 20」
        this.favorabilityManager.refreshAttributes();

        MaidSubConfig.load(tag, this);
        this.attackList = AttackListData.load(tag);
        if (tag.contains("MaidBackpackType", net.minecraft.nbt.Tag.TAG_STRING)) {
            ResourceLocation backpackId = parseId(tag.getString("MaidBackpackType"));
            if (backpackId != null) {
                this.setMaidBackpackType(BackpackManager.findBackpack(backpackId)
                        .orElseGet(BackpackManager::getEmptyBackpack));
            }
        }
    }

    // ---------------------------------------------------------------- 每刻

    /**
     * 睡觉时把女仆摆到床面上（上游 {@code EntityMaid#onMaidSleep}，由 {@code baseTick} 每 tick 调用）。
     *
     * <p>原版 {@code startSleeping} 只把坐标设成床头格中心 + 某个高度，之后每一 tick 仍然走正常的
     * {@code aiStep} / 重力 / 推挤，女仆会从床上滑下去；一旦离开床面就会被叫醒。
     * 上游的做法是每 tick 重申一次坐标、清零速度并静音（睡着的时候不该再触发语音）。</p>
     *
     * <p>这里<b>两端都要跑</b>：客户端也必须停在同一坐标上，否则会看到女仆在床上抖动，
     * {@code Pose.SLEEPING} 触发的 {@code sleep} 动画也会断续。</p>
     */
    private void onMaidSleep() {
        if (this.isSleeping()) {
            this.getSleepingPos().ifPresent(this::pinToBed);
            this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            if (!this.isSilent()) {
                this.setSilent(true);
            }
        } else {
            if (this.isSilent()) {
                this.setSilent(false);
            }
        }
    }

    /**
     * 把女仆放到两格床的正中、床面上。
     *
     * <h2>水平：床头格中心再退半格</h2>
     * <p>{@code MaidBedTask} 只用床头（{@code PART == HEAD}）那一格的 POI 入睡，
     * 所以睡眠坐标永远是床头格。而两格床的中点在它沿 {@code FACING} 反方向半格处。
     * 把坐标放在中点，再让渲染侧回退半个身长
     * （见 {@code GeoReplacedEntityRenderer#render}），
     * 1.5 格高的身体就正好以床心为中心铺满两格。</p>
     *
     * <h2>竖直：0.6875，直接照抄原版 {@code setPosToBed}</h2>
     * <p>值取自 vanilla {@code LivingEntity#setPosToBed}（javap 确认）：</p>
     * <pre>
     * setPos(pos.getX() + 0.5, pos.getY() + 0.6875, pos.getZ() + 0.5)
     * </pre>
     * <p>{@code 0.6875} = 11/16，比床面（{@code BlockMaidBed} 碰撞箱 9/16 = 0.5625）
     * 高 2/16。这个余量是原版给「趴着的身体」留的：模型是绕脚部原点旋转的，
     * 躺平后身体的横截面整体位于实体 Y 这一层，所以实体 Y 必须比床面高出半个身体厚度，
     * 否则就会陷进床里 —— 这正是之前用 0.5625（= 床面高度）时的现象。</p>
     */
    private void pinToBed(BlockPos headPos) {
        double cx = headPos.getX() + 0.5D;
        double cz = headPos.getZ() + 0.5D;
        Direction facing = com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed
                .getMaidBedFacing(this.level, headPos);
        if (facing != null) {
            cx -= facing.getStepX() * 0.5D;
            cz -= facing.getStepZ() * 0.5D;
        }
        this.setPos(cx, headPos.getY() + 0.6875D, cz);
    }

    @Override
    public void tick() {
        super.tick();
        // 被抱着时的位置两端都要算：乘客坐标不由服务端同步，客户端必须自己算一遍。
        // 放在 isClientSide 提前返回之前，否则客户端会把女仆留在抱起时的原地。
        this.updateCarriedPosition();
        this.onMaidSleep();
        if (this.level.isClientSide) {
            return;
        }
        // 离开座位（棋盘 / 坐垫）后清掉上一局的胜负表情
        this.gameRecordManager.tick();
        if (this.backpackDelay > 0) {
            this.backpackDelay--;
        }
        // Drive every bauble per-tick hook (upstream MaidTickEvent -> maidBauble.fireEvent).
        // Returning false keeps iterating so every bauble gets its tick.
        this.maidBauble.fireEvent((bauble, stack) -> {
            bauble.onTick(this, stack);
            return false;
        });
        // 好感度冷却表：吃饭 / 睡觉 / 玩棋等每种行为都有各自的冷却（见 Type）
        this.favorabilityManager.tick();

        // 副手自动举盾的倒计时（上游 passiveUseShieldTick）：
        // 到 1 时主动 stopUsingItem，否则女仆会一直保持举盾姿态。
        if (this.passiveUseShieldTick > 0) {
            this.passiveUseShieldTick--;
            if (this.passiveUseShieldTick == 1 && this.isUsingItem()
                    && this.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND) {
                this.stopUsingItem();
            }
        }

        // 作息坐标维护（上游 {@code SchedulePos#tick}）：每 40 tick 把活动范围挪到
        // 当前活动对应的点上，跑出范围就把寻路目标指回去、跑太远就直接传送。
        // 少了这一段，开/关 Home 按钮只改了标志位，玩家看不到任何行为变化。
        this.schedulePos.tick(this);
        // 坐姿位的两道保险（上游没有显式写，是因为它的 Brain 行为里会把女仆拉起来）：
        // 1. 落水时收回坐姿，否则「坐下 = 禁止移动」会让女仆永远泡在水里；
        // 2. 指令位与坐姿位不一致时跟随指令位，避免出现「实体已经起身、模型还坐着」。
        if (super.isInSittingPose()) {
            if (this.isInWaterOrBubble()) {
                this.setInSittingPose(false);
            } else if (!this.isOrderedToSit()) {
                super.setInSittingPose(false);
            }
        }
    }

    // ---------------------------------------------------------------- 受伤 / 盾牌

    /**
     * 副手自动举盾的剩余 tick（上游 {@code passiveUseShieldTick}）。
     */
    private int passiveUseShieldTick;

    /**
     * Damage settlement, ported from upstream EntityMaid#actuallyHurt.
     * Vanilla arithmetic plus one hook: right before health is subtracted it posts
     * MaidDamageEvent. Being after armour / resistance / absorption is deliberate -
     * getAmount() is then the damage that would really be taken, which is what the
     * protection baubles need in order to decide whether to spend durability.
     */
    @Override
    protected void actuallyHurt(net.minecraft.world.damagesource.DamageSource damageSrc, float damageAmount) {
        if (this.isInvulnerableTo(damageSrc)) {
            return;
        }
        damageAmount = this.getDamageAfterArmorAbsorb(damageSrc, damageAmount);
        damageAmount = this.getDamageAfterMagicAbsorb(damageSrc, damageAmount);
        float beforeAbsorption = damageAmount;
        damageAmount = Math.max(damageAmount - this.getAbsorptionAmount(), 0.0F);
        this.setAbsorptionAmount(this.getAbsorptionAmount() - (beforeAbsorption - damageAmount));
        float absorbed = beforeAbsorption - damageAmount;
        if (absorbed > 0.0F && absorbed < Float.MAX_VALUE / 10.0F
                && damageSrc.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.awardStat(net.minecraft.stats.Stats.DAMAGE_DEALT_ABSORBED, Math.round(absorbed * 10.0F));
        }

        com.github.tartaricacid.touhoulittlemaid.api.event.MaidDamageEvent event =
                new com.github.tartaricacid.touhoulittlemaid.api.event.MaidDamageEvent(this, damageSrc, damageAmount);
        damageAmount = net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event).isCanceled() ? 0.0F : event.getAmount();

        if (damageAmount == 0.0F) {
            return;
        }
        float health = this.getHealth();
        this.setHealth(health - damageAmount);
        this.getCombatTracker().recordDamage(damageSrc, health, damageAmount);
        this.setAbsorptionAmount(this.getAbsorptionAmount() - damageAmount);
        this.gameEvent(net.minecraft.world.level.gameevent.GameEvent.ENTITY_DAMAGED, damageSrc.getEntity());
    }

    /**
     * 受伤处理，移植自上游 {@code EntityMaid#hurt}。
     *
     * <p>两处与 vanilla 不同，都是上游行为：</p>
     * <ol>
     *     <li><b>主人（以及同队玩家）打自己的女仆，伤害除以 5 并封顶 2 点。</b>
     *         上游原注释就是「主人和同 Team 玩家对自己女仆的伤害数值为 1/5，最大为 2」。
     *         判定用 {@code isAlliedTo} —— {@code TamableAnimal} 重写了它，
     *         所以「主人本人」与「主人的队友」都会命中，而不只是玩家阵营。
     *         没有这一条，玩家一次全力横扫就能误杀自己的女仆。</li>
     *     <li><b>挨了投掷物且副手有盾牌时，自动举盾 5 秒（100 tick）。</b>
     *         注意这一下挨打仍然吃满伤害：举盾发生在伤害结算之后，
     *         从下一 tick 起才是举盾状态，后续投掷物才会被 {@code EntityDanmaku} 判定格挡。</li>
     * </ol>
     *
     * <p>上游还在这里发了 {@code MaidAttackEvent}（Forge 事件），本移植版没有对应事件总线，
     * 因此省略；其余分支逐条对应。</p>
     */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        // Upstream posts MaidAttackEvent first: cancelling means "this hit never landed",
        // which is how the nimble fabric bauble dodges projectiles.
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new com.github.tartaricacid.touhoulittlemaid.api.event.MaidAttackEvent(this, source, amount)).isCanceled()) {
            return false;
        }
        if (source.getEntity() instanceof net.minecraft.world.entity.player.Player player
                && this.isAlliedTo(player)) {
            amount = net.minecraft.util.Mth.clamp(amount / 5.0F, 0.0F, 2.0F);
            return super.hurt(source, amount);
        }
        if (source.isProjectile() && this.canUseShield()) {
            boolean usingShield = this.isUsingItem()
                    && this.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND;
            if (!usingShield) {
                this.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
                this.passiveUseShieldTick = 100;
            }
        }
        return super.hurt(source, amount);
    }

    /**
 * 举盾姿态（上游 {@code EntityMaid#isBlocking}）。
     *
     * <p>上游用 Forge 的 {@code ToolActions.SHIELD_BLOCK} 判定，以兼容模组盾牌；
     * 1.18.2 没有 ToolActions，这里退回 vanilla 的 {@code ShieldItem} 判定。
     * 写成完整条件（{@code isUsingItem} + 手上确实是盾）是为了让
     * 「自动举盾」那 100 tick 里 {@code isBlocking()} 一定为真 ——
     * {@code EntityDanmaku} 的格挡判定就靠它。</p>
     */
    @Override
    public boolean isBlocking() {
        return this.isUsingItem() && !this.getUseItem().isEmpty()
                && this.getUseItem().getItem() instanceof net.minecraft.world.item.ShieldItem;
    }


    // ---------------------------------------------------------------- IMaid

    @Override
    public EntityMaid asEntity() {
        return this;
    }

    @Override
    public ItemStack[] getHandItemsForAnimation() {
        return this.handItemsForAnimation;
    }

    @Override
    public boolean isOnClimbable() {
        return this.onClimbable();
    }

    /**
     * 是否装备了背包，供 Molang {@code tlm_has_backpack} 使用。
     */
    @Override
    public boolean hasBackpack() {
        return this.getMaidBackpackType() != BackpackManager.getEmptyBackpack();
    }

    // ---------------------------------------------------------------- 等级 / 表现数据

    /**
     * 女仆经验值（Molang {@code query.player_level} 与后续等级系统使用）。
     */
    /**
     * 女仆经验值。
     *
     * <p>与上游一致用 {@code int}：经验球的值本来就是整数，
     * 经验修补按 {@code 1 XP = 2 耐久} 折算时也只取整数部分。</p>
     */
    public int getExperience() {
        return this.entityData.get(DATA_EXPERIENCE);
    }

    public void setExperience(int experience) {
        this.entityData.set(DATA_EXPERIENCE, Math.max(0, experience));
    }

    /**
     * 死亡时是否总是掉经验（上游 {@code isAlwaysExperienceDropper} 返回 {@code true}）。
     *
     * <p>原版只有玩家与「被玩家击杀的怪物」掉经验；这里让女仆<b>无论怎么死</b>都掉，
     * 数量就是她自己攒的那些（见 {@link #getExperienceReward}）。
     * 少了这一条，女仆带着几百经验被岩浆吞掉就全没了。</p>
     */
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return true;
    }

    @Override
    protected int getExperienceReward(Player player) {
        return this.getExperience();
    }


    /**
     * 远程攻击类任务用的「能否看见目标」。
     *
     * <p>原版默认的攻击识别范围固定死 16 格，而一些远程武器希望超视距打击，
     * 因此把判定下放给 Task（上游 {@code EntityMaid#canSee}）。
     * 1.18.2 没有 {@code LivingEntity#canSee}，兜底用 {@code BehaviorUtils.canSee}。</p>
     */
    public boolean canSee(LivingEntity target) {
        if (this.getTask() instanceof IRangedAttackTask rangedTask) {
            return rangedTask.canSee(this, target);
        }
        return net.minecraft.world.entity.ai.behavior.BehaviorUtils.canSee(this, target);
    }

    /**
     * 攻击目标资格判定（上游 {@code EntityMaid#canAttack}）。
     *
     * <p><b>这个重写必不可少：</b>Brain 的 {@code StartAttacking} / {@code MeleeAttack}
     * 走的都是 vanilla 的 {@code Mob#canAttack(LivingEntity)}。若只实现
     * {@link com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask#canAttack}
 * 却不在这里转发，任务里的敌我判定<b>永远不会被调用</b>，
     * 结果就是女仆会把其他女仆（同样是 {@code TamableAnimal}）当成敌人互相攻击。</p>
     */
    @Override
    public boolean canAttack(LivingEntity target) {
        IMaidTask maidTask = this.getTask();
        if (maidTask instanceof com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask attackTask) {
            return attackTask.canAttack(this, target);
        }
        return super.canAttack(target);
    }

    /**
     * 上游 {@code EntityMaid#canAttackType}：额外排除盔甲架。
     */
    @Override
    public boolean canAttackType(EntityType<?> type) {
        return type != EntityType.ARMOR_STAND && super.canAttackType(type);
    }

    /**
     * 被主人抱着（鞍）时，每 tick 把位置贴到「玩家身前 0.75 格、抬高 0.15 格」，
     * 朝向对齐玩家<b>上半身骨骼</b>的偏航角（{@code yBodyRot}）。
     *
     * <h2>为什么需要</h2>
     *
     * <p>乘客的坐标<b>不由服务端同步</b>：服务端只发「谁骑谁」
     * （{@code ClientboundSetPassengersPacket}），具体坐标由客户端自己算。
     * 原版算它的地方是 {@code Entity#rideTick()} 最后那一句
     * {@code getVehicle().positionRider(this)}，而 {@code rideTick()} 只会在
     * {@code Level#tickPassenger} 里被调到。</p>
     *
     * <p>反编译 {@code ClientLevel} 确认这条路径<b>会</b>执行（字节码顺序是
     * {@code setOldPosAndRot → tickCount++ → rideTick}，且只对
     * {@code tickingEntities} 里的实体执行 —— 女仆在表里）。
     * 但它依赖「客户端确实复现了乘客关系」，一旦哪个环节没对上，画面就会永远停在
     * 抱起那一刻。所以在 {@link #tick()} 里再算一遍作为兜底：两端结果完全一致，
     * 不需要任何额外网络包。</p>
     *
     * <h2>为什么绝不能在这里调 setOldPosAndRot()</h2>
     *
     * <p>理由就是上一段：{@code tickPassenger} 已经替我们调过了。渲染器画的是
     * {@code lerp(partialTick, xo, getX())}，而 {@code xo} 的语义是「上一 tick 的位置」。
     * 反编译 ClientLevel 确认：乘客每次 tick 之前，tickPassenger() 已经替她调过一次
     * setOldPosAndRot()（字节码顺序：setOldPosAndRot → tickCount++ → rideTick）。
     * 所以「旧位置 → 新位置」之间本来就有插值。
     * 这里再调一次会把 xo 覆盖成新位置，插值区间长度变成 0 ——
     * 表现正是「每 tick 瞬移到玩家身前一次」，看起来只有 20 帧。
     * 这是之前「女仆跟随不平滑」的根因。</p>
     */


    public void updateCarriedPosition() {
        Player carrier = this.resolveCarrier();
        if (carrier == null) {
            // 载具没了就把同步 id 清掉（只清服务端那一份，客户端等同步即可）
            if (!this.level.isClientSide && this.getCarrierId() != 0) {
                this.setCarrierId(0);
            }
            return;
        }
        if (!this.level.isClientSide && this.getCarrierId() != carrier.getId()) {
            this.setCarrierId(carrier.getId());
        }
        // 朝向跟随载具的「上半身骨骼」——必须用 yBodyRot，不能用 getYRot()。
        // getYRot 是头部朝向，玩家一扭头女仆就跟着甩；yBodyRot 才是身体骨骼的偏航角，
        // 也正是 positionRider 里算偏移量用的那个角度。
        this.setYRot(carrier.yBodyRot);
        this.setYHeadRot(carrier.yBodyRot);
        this.setYBodyRot(carrier.yBodyRot);

        // yBodyRot 是顺时针角度，而 Vec3#yRot 用的是逆时针，所以取负（上游同款）
        float radians = (float) -Math.toRadians(carrier.yBodyRot);
        Vec3 offset = carrier.position().add(new Vec3(0, 0, 0.75).yRot(radians));
        // 玩家下坠时手臂位置更低；不跟着降会看起来悬在半空（上游同款：0.15，下坠时 -0.3）
        double yOffset = 0.15D;
        if (carrier.isDescending()) {
            yOffset -= 0.3D;
        }
        this.setPos(offset.x, offset.y + yOffset, offset.z);

        // 绝对不要在这里调 setOldPosAndRot()。
        //
        // 渲染器画的是 lerp(partialTick, xo, getX())，xo 的正确语义是「上一 tick 的位置」。
        // 反编译 ClientLevel 确认：乘客每次 tick 之前，tickPassenger() 已经替她调过一次
        // setOldPosAndRot()（字节码顺序：setOldPosAndRot → tickCount++ → rideTick）。
        // 所以「旧位置 → 新位置」之间本来就有插值。
        // 这里再调一次会把 xo 覆盖成新位置，插值区间长度变成 0 ——
        // 表现正是「每 tick 瞬移到玩家身前一次」，看起来只有 20 帧。
    }

    /**
     * 谁正抱着她：优先用真正的乘客关系，其次用同步过来的 {@link #getCarrierId()}。
     *
     * <p>后半段是必需的兜底：乘客的坐标不由服务端下发，如果客户端因为任何原因
     * 没拿到「乘客关系」，她就再也不会被计算位置，画面会一直停在抱起那一刻。</p>
     */
    @Nullable
    private Player resolveCarrier() {
        if (this.getVehicle() instanceof Player player) {
            return player;
        }
        int id = this.getCarrierId();
        if (id != 0 && this.level.getEntity(id) instanceof Player player) {
            return player;
        }
        return null;
    }

    public int getCarrierId() {
        return this.entityData.get(DATA_CARRIER_ID);
    }

    public void setCarrierId(int id) {
        this.entityData.set(DATA_CARRIER_ID, id);
    }

    /**
     * 是否正被玩家抱着（鞍）。
     *
     * <p>只看同步字段：{@code carrierId != 0} 就是「有人抱着她」，再补一条真正的
     * 乘客关系判断（抱起的第一个 tick 里同步数据可能还没到）。</p>
     *
     * <p><b>不能只看 {@code isPassenger()}：</b>乘客关系是两端<b>各自复现</b>的
     * （服务端 {@code startRiding}，客户端靠 {@code ClientboundSetPassengersPacket} 再来一次），
     * 一旦某一端没复现成功，所有以它为前提的逻辑（AI、推挤、渲染、玩家手臂姿势）
     * 就全部失效 —— 这正是「马鞍抱起后女仆还推着玩家走、玩家手臂也不抱」的根因。</p>
     */
    public boolean isCarried() {
        return this.getCarrierId() != 0 || this.getVehicle() instanceof Player;
    }

    /**
     * 「被抱着，或者确实挂着某个载具」—— 渲染层需要同时覆盖两种情形。
     *
     * <p>上游只需要判 {@code isPassenger()}，因为它的抱持就是乘客关系；
     * 本移植版多了一条「同步字段 {@code carrierId}」的兜底，
     * 所以判定要写成两者取或，见 {@link #isCarried()}。</p>
     */
    public boolean isCarriedOrPassenger() {
        return this.isCarried() || this.isPassenger();
    }

    /**
     * 被抱着时不参与实体互相推挤。
     *
     * <p>反编译 {@code LivingEntity#isPushable()} 得到的是
     * {@code isAlive() && !isSpectator() && !onClimbable()} —— <b>一点都不含糊地返回 true</b>。
     * 于是 {@code Entity#pushEntities()} 里那个
     * {@code getEntities(this, getBoundingBox(), Entity::isPushable)} 会同时命中
     * 「玩家推女仆」和「女仆推玩家」。她正好卡在玩家身前 0.75 格，包围盒与玩家的重叠，
     * 表现就是「抱着的时候女仆还在推着玩家走」。</p>
     *
     * <p>原版 {@code Entity#pushEntities} 里那条 {@code isPassengerOfSameVehicle} 的豁免
     * 只对「真的挂上了乘客关系」的实体生效，而我们的抱持并不保证乘客关系两端一致，
     * 所以这里直接给出确定的判据。</p>
     */
    @Override
    public boolean isPushable() {
        return !this.isCarried() && super.isPushable();
    }

    /**
     * 被抱着时<b>不可被准星选中</b>。
     *
     * <h2>为什么需要</h2>
     *
     * <p>被鞍抱起后她正好悬在玩家身前 0.75 格，而准星射线（{@code GameRenderer#pick}）
     * 只取「最近的那个」，所以她必然挡在中间。此时右键任何东西都会变成
     * {@code mobInteract}，一路走到 {@code openMaidGui} —— 表现就是
     * 「抱着女仆想开箱子，结果打开了女仆界面」。</p>
     *
     * <p>反编译确认这条链：{@code GameRenderer} 里实体射线的过滤器就是
     * <pre>
     * private static boolean method_18144(Entity e) {
     *     return !e.isSpectator() &amp;&amp; e.isPickable();
     * }
     * </pre>
     * 而 {@code Minecraft#startUseItem} 的实体分支一旦命中，就
     * <b>不会再回头去试方块</b>（{@code case ENTITY → interactAt → interact → return}），
     * 所以只在 {@code mobInteract} 里拒绝是没用的，必须让她从射线里消失。</p>
     *
     * <h2>与上游一致的做法</h2>
     *
     * <p>上游 1.20.1 的 {@code EntityBroom} 对同一个问题就是同一套解法：
     * <pre>
     * public boolean isPickable() {
     *     boolean hasPlayer = ...;   // 有玩家骑着
     *     return hasPlayer ? false : super.isPickable();
     * }
     * </pre>
     * 上游 {@code EntityMaid} 本身没有这个重写，但它的抱持位置同樣是身前 0.75 格，
     * 遇到的是同一个坑。</p>
     *
     * <p>放下她仍然有路可走，且都不经过实体射线：拿鞍右键空气 / 方块
     * （{@code MaidSaddleEvents} 挂在 {@code UseItemCallback} 上）。
     * 射线穿透她之后，被挡在后面的箱子、门、拉杆都有正常响应。</p>
     */
    @Override
    public boolean isPickable() {
        return !this.isCarried() && super.isPickable();
    }

    /**
     * 该玩家此刻是否正抱着某只女仆（供客户端渲染判定使用）。
     *
     * <p>优先看真正的乘客关系；那条路径两端是各自复现的，可能没对上，
     * 所以退一步按同步字段 {@code carrierId} 在玩家身边再找一遍。
     * 这里查的是 2 格见方的小盒子，玩家模型每帧调一次也是可忽略的开销。</p>
     */
    public static boolean isCarryingMaid(Player player) {
        if (player.getFirstPassenger() instanceof EntityMaid) {
            return true;
        }
        List<Entity> list = player.level.getEntities(player,
                player.getBoundingBox().inflate(2.0D),
                entity -> entity instanceof EntityMaid maid && maid.getCarrierId() == player.getId());
        return !list.isEmpty();
    }

    /**
     * 乘客每一 tick 的朝向同步，逐字照抄上游 {@code EntityMaid#rideTick}（1.18.2 第 516–524 行）：
     *
     * <pre>
     * public void rideTick() {
     *     super.rideTick();
     *     if (this.getVehicle() != null) {
     *         Entity vehicle = this.getVehicle();
     *         this.setYHeadRot(vehicle.getYRot());
     *         this.setYBodyRot(vehicle.getYRot());
     *     }
     * }
     * </pre>
     *
     * <p><b>为什么少了这一段就会「固定看向一侧」：</b>反编译 {@code Entity#rideTick} 可以看到它是
     * {@code setOldPosAndRot → tickCount++ → rideTick} 最后那一句
     * {@code getVehicle().positionRider(this)}，而 {@code rideTick()} 只会在
     * {@code Level#tickPassenger} 里被调到。</p>
     *
     * <p>反编译 {@code ClientLevel} 确认这条路径<b>会</b>执行（字节码顺序是
     * {@code setOldPosAndRot → tickCount++ → rideTick}，且只对
     * {@code tickingEntities} 里的实体执行 —— 女仆在表里）。
     * 但它依赖「客户端确实复现了乘客关系」，一旦哪个环节没对上，画面就会永远停在
     * 抱起那一刻。所以在 {@link #tick()} 里再算一遍作为兜底：两端结果完全一致，
     * 不需要任何额外网络包。</p>
     *
     * <h2>为什么绝不能在这里调 setOldPosAndRot()</h2>
     *
     * <p>理由就是上一段：{@code tickPassenger} 已经替我们调过了。渲染器画的是
     * {@code lerp(partialTick, xo, getX())}，而 {@code xo} 的语义是「上一 tick 的位置」。
     * 反编译 ClientLevel 确认：乘客每次 tick 之前，tickPassenger() 已经替她调过一次
     * setOldPosAndRot()（字节码顺序：setOldPosAndRot → tickCount++ → rideTick）。
     * 所以「旧位置 → 新位置」之间本来就有插值。
     * 这里再调一次会把 xo 覆盖成新位置，插值区间长度变成 0 ——
     * 表现正是「每 tick 瞬移到玩家身前一次」，看起来只有 20 帧。
     * 这是之前「女仆跟随不平滑」的根因。</p>
     */


    public void rideTick() {
        super.rideTick();
        if (this.getVehicle() != null) {
            Entity vehicle = this.getVehicle();
            this.setYHeadRot(vehicle.getYRot());
            this.setYBodyRot(vehicle.getYRot());
        }
        this.updateCarriedPosition();
    }

    /**
     * 「不可骑乘」时拦住所有挂载请求（上游 {@code MaidMountEvent} 在 {@code EntityMountEvent}
     * 上做的事）。
     *
     * <p>上游是在 Forge 的 {@code EntityMountEvent} 里取消的，范围覆盖<b>一切</b>载具 ——
     * 鞍、船、矿车、其他模组的坐骑都算。Fabric 没有对应事件，因此在
     * {@code startRiding} 这一层等价地拦一次。</p>
     *
     * <p><b>为什么只判服务端、且跳过 {@code force}：</b>乘客关系由服务端决定，客户端是
     * 收 {@code ClientboundSetPassengersPacket} 后调 {@code startRiding(vehicle, true)}
     * 复现的（{@code force == true}）。如果客户端也拦，会和服务端不一致 ——
     * 服务端认为她骑着，客户端却没挂上，位置就再也不跟随了。</p>
     */
    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        if (!this.level.isClientSide && !force) {
            // 对齐上游 {@code MaidMountEvent}，两条规则都不能漏：
            //
            // 1. 座位（{@code EntitySit}）不受「女仆骑乘」开关影响。上游写的就是
            //    {@code !(entityBeingMounted instanceof EntitySit)}。这个开关管的是「抱着她走」，
            //    而棋盘 / 坐垫的座位是她自己走过去坐下的——关掉骑乘不该让她
            //    连下棋都做不到。漏了这一条，棋盘会「看起来能坐、实际坐不下」。
            // 2. 船上已经放了坐垫、而坐垫还空着时，优先去坐坐垫。否则她会直接
            //    坐进船里，坐垫永远空着。
            if (!(vehicle instanceof com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit)) {
                if (!this.isRideable()) {
                    return false;
                }
                if (vehicle instanceof net.minecraft.world.entity.vehicle.Boat boat
                        && boat.getControllingPassenger()
                        instanceof com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair chair
                        && chair.getPassengers().isEmpty()) {
                    return false;
                }
            }
        }
        return super.startRiding(vehicle, force);
    }

    /**
     * 下车的唯一出口：顺手把「谁抱着她」清掉。
     *
     * <p>放下的路径有好几条（再右击一次、拿鞍右击空气、潜行右击、主人死亡），
     * 它们最终都会走到 {@code stopRiding()}（{@code ejectPassengers()} 内部也是逐个调它），
     * 所以清理逻辑放在这里就不会漏。</p>
     */
    @Override
    public void stopRiding() {
        super.stopRiding();
        if (!this.level.isClientSide) {
            this.setCarrierId(0);
        }
    }

    // ================================================================ 交互 / 驯服
    //
    // 说明：下面这一整段按上游 InteractMaidEvent 各订阅者的**优先级顺序**逐个移植。
    // Fabric 没有事件总线，所以全部在 mobInteract 里显式串起来。

    /**
     * 女仆的钓鱼钩（上游 public 字段，行为树与任务都直接读它）。
     */
    public com.github.tartaricacid.touhoulittlemaid.entity.projectile.MaidFishingHook fishing = null;

    /**
     * 界面是否正开着。开着时 Brain 不该继续驱动移动，否则女仆会在界面背后乱走。
     */
    private boolean guiOpening = false;

    public boolean isGuiOpening() {
        return this.guiOpening;
    }

    public void setGuiOpening(boolean opening) {
        this.guiOpening = opening;
        if (opening) {
            this.getNavigation().stop();
        }
    }

    /**
     * 附魔之瓶兑换：每 12 点经验换 1 瓶（上游 GetExpBottleEvent 的 PER_BOTTLE_XP）。
     */
    private static final int PER_BOTTLE_XP = 12;

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // 归属判定必须用 UUID 直接比较，不能用 isOwnedBy(player)：
        // 区块卸载后 getOwner() 可能一瞬间取不到玩家实体，于是「已归属」分支被跳过。
        java.util.UUID ownerUUID = this.getOwnerUUID();
        if (ownerUUID != null && ownerUUID.equals(player.getUUID())) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.interactLivingEntity(player, this, hand).consumesAction()) {
                return InteractionResult.SUCCESS;
            }
            // 原版对主手 + 副手各调一次 mobInteract；只让主手处理，避免一次点击切两次坐姿。
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            // 玻璃瓶换附魔之瓶必须排在开界面之前（上游 GetExpBottleEvent 的顺序）
            InteractionResult xpBottleResult = this.takeXpWithBottle(player, stack);
            if (xpBottleResult != null) {
                return xpBottleResult;
            }

            // Substitute jizo: hand it to the maid to make her permanently invulnerable.
            InteractionResult jizoResult = this.applySubstituteJizo(player, stack);
            if (jizoResult != null) {
                return jizoResult;
            }
            InteractionResult backpackResult = this.maidBackpackInteract(player, stack);
            if (backpackResult != null) {
                return backpackResult;
            }
            // 以下四项与上游 InteractMaidEvent 的处理器一一对应，顺序也照抄
            // （上游按优先级 NORMAL / LOW / LOWEST 排队，这里就是同一个顺序）。
            InteractionResult potionResult = this.applyPotionToMaid(player, stack);
            if (potionResult != null) {
                return potionResult;
            }
            InteractionResult appleResult = this.feedGoldenApple(player, stack);
            if (appleResult != null) {
                return appleResult;
            }

            // 好感度工具：上游 UseFavorabilityToolEvent（InteractMaidEvent，NORMAL 优先级）。
            // 三个变体分别加 / 减 / 拉满，潜行时只走 1 点，否则 64 点。
            InteractionResult favorabilityResult = this.useFavorabilityTool(player, stack);
            if (favorabilityResult != null) {
                return favorabilityResult;
            }

            InteractionResult saddleResult = this.saddleMaid(player, stack);
            if (saddleResult != null) {
                return saddleResult;
            }
            InteractionResult specialResult = this.compassOrNameTag(player, stack);
            if (specialResult != null) {
                return specialResult;
            }
            // 上游 DismountMaidEvent（LOW）排在 SwitchSittingEvent（LOWEST）之前
            if (player.isShiftKeyDown() && this.tryDismount()) {
                return InteractionResult.sidedSuccess(this.level.isClientSide);
            }
            // 上游 SwitchSittingEvent 明确排除河童的罗盘：那件物品的潜行右击是
            // 「清空作息点」，不是「坐下 / 起身」。
            if (player.isShiftKeyDown()
                    && !stack.is(com.github.tartaricacid.touhoulittlemaid.init.InitItems.KAPPA_COMPASS)) {
                this.setInSittingPose(!this.isMaidInSittingPose());
                this.jumping = false;
                this.getNavigation().stop();
                // 上游同款：起身 / 坐下后把活动范围重新算一遍，并让她往新中心走。
                if (!this.level.isClientSide && this.hasRestriction() && this.canBrainMoving()) {
                    this.schedulePos.restrictTo(this);
                    net.minecraft.world.entity.ai.behavior.BehaviorUtils
                            .setWalkAndLookTargetMemories(this, this.getRestrictCenter(), 0.7F, 3);
                }
                // 与上游 SwitchSittingEvent 一致：原版拾取音效，音调抬高一个八度
                this.playSound(SoundEvents.ITEM_PICKUP, 0.2F,
                        ((this.getRandom().nextFloat() - this.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
                return InteractionResult.sidedSuccess(this.level.isClientSide);
            }
            // 空手右键（且未潜行）打开女仆界面。上游 openMaidGui 是**无条件**的。
            // 被抱着时不打开界面：她正好悬在准星正前方，任何右键都容易落到她身上，
            // 表现就是「抱着女仆想开箱子，结果弹出了女仆界面」。
            // 正常情况下她此时已经由 {@link #isPickable()} 从准星射线里排除，
            // 这一句是给「客户端状态不同步 / 别的模组自己发交互包」留的保险。
            if (this.isCarried()) {
                return InteractionResult.PASS;
            }
            this.openMaidGui(player, TabIndex.MAIN);
            return InteractionResult.sidedSuccess(this.level.isClientSide);
        }
        return this.tameMaid(player.getItemInHand(hand), player);
    }


    /**
     * 好感度工具（上游 {@code event/maid/UseFavorabilityToolEvent}）。
     *
     * <p>与上游一一对应：</p>
     * <ul>
     *     <li>{@code add} → {@code manager.add(point)}，受等级上限钳制；</li>
     *     <li>{@code reduce} → {@code manager.reduceWithoutLevel(point)} —— <b>允许掉级</b>。
     *         这个「WithoutLevel」正是上游为工具特意选的：换成 {@code reduce} 的话，
     *         减到本级下限就再也减不动了，工具会看起来「点不动」；</li>
     *     <li>{@code full} → {@code manager.max()}。</li>
     * </ul>
     *
     * <p>{@code point} 是潜行 1 / 否则 64，与上游一致。</p>
     *
     * <p>声音与上游一样<b>无条件</b>播放 {@code PLAYER_LEVELUP}。注意 {@code add}
     * 在升阶时自己也会播一次，所以「刚好升阶」的那一下会听到两次 —— 上游就是这个
     * 行为，这里保持逐字一致，不做所谓「优化」。</p>
     *
     * @return {@code null} 表示手里不是好感度工具，交给后面的处理器
     */
    @Nullable
    private InteractionResult useFavorabilityTool(Player player, ItemStack stack) {
        boolean isAdd = stack.is(com.github.tartaricacid.touhoulittlemaid.init.InitItems.FAVORABILITY_TOOL_ADD);
        boolean isReduce = stack.is(com.github.tartaricacid.touhoulittlemaid.init.InitItems.FAVORABILITY_TOOL_REDUCE);
        boolean isFull = stack.is(com.github.tartaricacid.touhoulittlemaid.init.InitItems.FAVORABILITY_TOOL_FULL);
        if (!isAdd && !isReduce && !isFull) {
            return null;
        }
        if (this.level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        int point = player.isShiftKeyDown() ? 1 : 64;
        com.github.tartaricacid.touhoulittlemaid.entity.favorability.FavorabilityManager manager =
                this.getFavorabilityManager();
        if (isAdd) {
            manager.add(point);
        } else if (isReduce) {
            manager.reduceWithoutLevel(point);
        } else {
            manager.max();
        }
        this.playSound(SoundEvents.PLAYER_LEVELUP, 0.5F,
                this.getRandom().nextFloat() * 0.1F + 0.9F);
        return InteractionResult.SUCCESS;
    }

    /**
     * 把药水 / 奶桶喂给女仆（上游 ApplyPotionEffectEvent）。
     *
     * <p>必须潜行：这两个动作和「切换坐姿」共用同一个潜行右键，所以必须排在坐姿切换之前
     * 并吃掉这次交互。药水是「女仆喝」而不是「被泼」，因此只认 {@code Items.POTION}，
     * 喷溅 / 滞留药水不在其列。</p>
     *
     * @return {@code null} 表示手里不是药水 / 奶桶，或没潜行
     */
    @Nullable
    private InteractionResult applyPotionToMaid(Player player, ItemStack stack) {
        if (!player.isShiftKeyDown()) {
            return null;
        }
        boolean isPotion = stack.is(Items.POTION);
        boolean isMilk = stack.is(Items.MILK_BUCKET);
        if (!isPotion && !isMilk) {
            return null;
        }
        if (this.level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (isPotion) {
            stack.getItem().finishUsingItem(stack.copy(), this.level, this);
        } else {
            // 1.18.2 的 LivingEntity 没有 curePotionEffects，退回原版奶桶自己用的 removeAllEffects
            this.removeAllEffects();
        }
        if (!player.isCreative()) {
            ItemStack remainder = new ItemStack(isPotion ? Items.GLASS_BOTTLE : Items.BUCKET);
            stack.shrink(1);
            ItemHandlerHelper.giveItemToPlayer(player, remainder);
        }
        this.playSound(SoundEvents.GENERIC_DRINK, 0.6F, 0.8F + this.level.random.nextFloat() * 0.4F);
        return InteractionResult.SUCCESS;
    }

    /**
     * 把金苹果 / 附魔金苹果喂给女仆（上游 ApplyGoldenAppleEvent）。
     *
     * <p>上游比较的是 {@code FoodProperties} 的<b>对象身份</b>而不是物品本身，
     * 目的是让「用同样食物属性注册的自定义金苹果」也能生效。这里照抄这个判据。</p>
     *
     * @return {@code null} 表示手里不是金苹果，或没潜行
     */
    @Nullable
    private InteractionResult feedGoldenApple(Player player, ItemStack stack) {
        if (!player.isShiftKeyDown()) {
            return null;
        }
        net.minecraft.world.food.FoodProperties food = stack.getItem().getFoodProperties();
        if (food != net.minecraft.world.food.Foods.GOLDEN_APPLE
                && food != net.minecraft.world.food.Foods.ENCHANTED_GOLDEN_APPLE) {
            return null;
        }
        if (this.level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        this.eat(this.level, stack);
        return InteractionResult.SUCCESS;
    }

    /**
     * 用鞍把女仆抱起来 / 放下（上游 SaddleMaidEvent + CancelSaddleMaidEvent）。
     *
     * <p><b>实现方式是「女仆成为玩家的乘客」</b>，而不是给女仆加一个骑乘状态。
     * 乘客位置由 {@code positionRider}（mixin）+ {@code updateCarriedPosition()} 改写成
     * 「玩家身前 0.75 格」，视觉上就是被抱在怀里。</p>
     *
     * @return {@code null} 表示手里不是鞍
     */
    @Nullable
    private InteractionResult saddleMaid(Player player, ItemStack stack) {
        if (!stack.is(Items.SADDLE)) {
            return null;
        }
        if (this.level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.getPassengers().isEmpty() && this.getPassengers().isEmpty()) {
            if (this.isRideable() && this.startRiding(player)) {
                this.saddleMountTick = this.tickCount;
                // 「谁抱着她」也要同步给客户端：乘客坐标本来就不下发，
                // 客户端只能靠这个 id 自己算位置（见 updateCarriedPosition）
                this.setCarrierId(player.getId());
                if (this.isHomeModeEnable()) {
                    this.setHomeModeEnable(false);
                }
                player.displayClientMessage(
                        new TranslatableComponent("message.touhou_little_maid.saddle.how_to_eject"), true);
            } else {
                // 抱不起来时必须说清原因。
                //
                // 「静默 SUCCESS」是这个功能最难查的地方：玩家看到的是「马鞍右键没反应」，
                // 日志里什么都没有，而且**只有部分女仆**会这样 —— 因为失败条件全是
                // 每只女仆各自的状态（骑乘开关、是否已经在骑别的东西）。
                // 上游同样是静默的（MaidMountEvent 直接 cancel 掉 EntityMountEvent），
                // 但上游玩家能靠「女仆骑乘」按钮的位置猜到；这里给一句明确提示更省事。
                this.reportSaddleFailure(player);
            }
            return InteractionResult.SUCCESS;
        }
        // 上游此处不再判「抱着的是不是女仆」，直接全部放下 ——
        // 能走到这条分支说明玩家背上确实有乘客，而鞍就是「放下来」的指令。
        if (!player.getPassengers().isEmpty()) {
            player.ejectPassengers();
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 抱不起来时给玩家一句具体原因，并记一条 WARN。
     *
     * <p>三条失败路径互斥，按「最容易误解」的顺序判定：</p>
     * <ol>
     *     <li>{@code isRideable() == false} —— 主界面左下角第三个按钮（{@code 女仆骑乘}）被关掉了。
     *         它同时是「用鞍抱起女仆」的总开关，与上游 {@code MaidMountEvent} 一致。</li>
     *     <li>她正骑着别的东西（坐垫 / 船 / 扫帚 / 别的载具）。原版
     *         {@code Entity#startRiding} 会先 {@code stopRiding()} 再挂新载具，
     *         但如果连 {@code canRide} 都没过（见下一条），这一步根本不会执行。</li>
     *     <li>原版 {@code Entity#canRide} 的两个条件：{@code boardingCooldown <= 0} 与
     *         {@code !isShiftKeyDown()}。前者在每次 {@code stopRiding()} 时被置为 60 tick，
     *         所以刚放下她、或她刚从一个载具上下来时，3 秒内再抱会失败 —— 这是原版行为，
     *         不是移植偏差，但必须说出来，否则玩家只会觉得「时灵时不灵」。</li>
     * </ol>
     */
    private void reportSaddleFailure(Player player) {
        String reason;
        if (!this.isRideable()) {
            reason = "message.touhou_little_maid.saddle.rideable_off";
        } else if (this.getVehicle() != null) {
            reason = "message.touhou_little_maid.saddle.riding_other";
        } else {
            reason = "message.touhou_little_maid.saddle.cooldown";
        }
        player.displayClientMessage(new TranslatableComponent(reason), true);
        TouhouLittleMaid.LOGGER.warn(
                "[Futo] Saddle pickup refused for maid id={} uuid={} reason={} rideable={} vehicle={} passengers={}",
                this.getId(), this.getUUID(), reason, this.isRideable(),
                this.getVehicle() == null ? "none" : this.getVehicle().getType().toString(),
                this.getPassengers().size());
    }

    /**
     * 指南针 / 命名牌：真正的表现在客户端，服务端这里只负责「吃掉这次交互」。
     *
     * <p>不开界面这一步是必须的 —— 否则会继续往下走到 {@link #openMaidGui}。</p>
     *
     * @return {@code null} 表示手里拿的不是指南针 / 空命名牌
     */
    @Nullable
    private InteractionResult compassOrNameTag(Player player, ItemStack stack) {
        if (stack.is(Items.COMPASS)) {
            // 原版指南针：把她的作息坐标发给这位玩家，客户端据此画三个区域环
            // （上游 MaidAreaClickEvent）。服务端只负责发数据。
            //
            // 清空作息点是**河童的罗盘**的事（ItemKappaCompass#interactLivingEntity），
            // 不是原版指南针的。早期版本把两者混在一起，于是「潜行右击女仆」在没有
            // 河童罗盘的情况下也会把作息点清掉，而且关掉了她的居家模式。
            if (!this.level.isClientSide && player instanceof ServerPlayer serverPlayer
                    && !this.schedulePos.getWorkPos().equals(BlockPos.ZERO)) {
                com.github.tartaricacid.touhoulittlemaid.network.MaidAreaPacket
                        .send(serverPlayer, this);
            }
            // 潜行时继续往下走：上游 SwitchSittingEvent 是 LOWEST 优先级、且不检查事件
            // 是否已被取消，所以「潜行 + 原版指南针」在原版里会同时画区域**并**切换坐姿。
            return player.isShiftKeyDown() ? null : InteractionResult.SUCCESS;
        }
        if (stack.is(Items.NAME_TAG) && !stack.hasCustomHoverName()) {
            return InteractionResult.SUCCESS;
        }
        return null;
    }

    /**
     * 潜行右键时「放下正在被抱 / 骑着的女仆」（上游 DismountMaidEvent）。
     *
     * @return {@code true} 表示确实放下了什么，调用方应就此结束本次交互
     */
    private boolean tryDismount() {
        if (this.getVehicle() == null && this.getCarrierId() == 0 && this.getPassengers().isEmpty()) {
            return false;
        }
        if (this.getVehicle() != null) {
            this.stopRiding();
        }
        if (!this.level.isClientSide) {
            this.setCarrierId(0);
        }
        if (!this.getPassengers().isEmpty()) {
            this.ejectPassengers();
        }
        return true;
    }

    /**
     * 上一次「被鞍抱起」的时刻（{@code tickCount}）。
     *
     * <p>用来屏蔽<b>同一次右键</b>引发的二次交互：原版对「右击实体」和「使用物品」
     * 是两条独立的派发路径，一次点击有可能两条都走到 —— 而
     * {@code MaidSaddleEvents}（拿鞍右击空气 = 放下）正是挂在「使用物品」上的。
     * 没有这个窗口，「拿鞍右击女仆抱起」会立刻被随后那次 {@code useItem} 放下来，
     * 表现就是「一抱起就自动放下」。</p>
     */
    private int saddleMountTick = Integer.MIN_VALUE / 2;

    public int getSaddleMountTick() {
        return this.saddleMountTick;
    }

    /**
     * 是否正被<b>主人</b>抱着（鞍）。
     *
     * <p>被主人抱着时，AI 不应该自作主张下车：那时玩家已经在直接控制她的位置了，
     * 「去干活」「躲危险」这类理由都不成立。</p>
     */
    public boolean isCarriedByOwner() {
        Player carrier = this.resolveCarrier();
        return carrier != null && this.isOwnedByPlayer(carrier);
    }

    /**
     * 能否被骑乘（上游 {@code EntityMaid#isRideable}）。主界面左下角第三个按钮切换。
     */
    public boolean isRideable() {
        return MaidSubConfig.isRideable(this);
    }

    public void setRideable(boolean rideable) {
        MaidSubConfig.setRideable(this, rideable);
    }

    /**
     * 背包交互：拿着背包物品右键 = 穿上，拿着剪刀右键 = 脱下（上游 {@code HandleBackpackEvent}）。
     *
     * <p>三条判定与上游逐条对应：女仆归属自己 + 不在换装冷却中 + 目标背包与当前不同。
     * 冷却 {@code backpackDelay} 是必需的 —— 原版对主手和副手各调一次 {@code mobInteract}，
     * 没有它一次右键就出现「穿上又立刻脱下」。</p>
     *
     * @return {@code null} 表示手里拿的不是背包相关物品
     */
    @Nullable
    private InteractionResult maidBackpackInteract(Player player, ItemStack stack) {
        IMaidBackpack current = this.getMaidBackpackType();
        if (stack.getItem() instanceof ShearsItem) {
            if (this.backpackHasDelay() || current == BackpackManager.getEmptyBackpack()) {
                return null;
            }
            if (this.level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            this.setBackpackDelay();
            player.getCooldowns().addCooldown(stack.getItem(), 20);
            // 先发回背包本体，再让背包自己处理「多出来的格子里的东西」
            ItemHandlerHelper.giveItemToPlayer(player, current.getTakeOffItemStack(stack, player, this));
            current.onTakeOff(stack, player, this);
            this.setMaidBackpackType(BackpackManager.getEmptyBackpack());
            stack.hurtAndBreak(1, player, broken -> broken.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            this.playSound(SoundEvents.HORSE_SADDLE, 0.5F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        IMaidBackpack backpack = BackpackManager.findBackpack(stack).orElse(null);
        if (backpack == null || backpack == BackpackManager.getEmptyBackpack() || backpack == current) {
            return null;
        }
        if (this.backpackHasDelay()) {
            return null;
        }
        if (this.level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        this.setBackpackDelay();
        player.getCooldowns().addCooldown(stack.getItem(), 20);
        // 顺序与上游一致：先脱旧的（可能把超出的格子里的东西丢出来），再换类型，最后穿上新的
        ItemHandlerHelper.giveItemToPlayer(player, current.getTakeOffItemStack(stack, player, this));
        current.onTakeOff(stack, player, this);
        this.setMaidBackpackType(backpack);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        backpack.onPutOn(stack, player, this);
        this.playSound(SoundEvents.HORSE_SADDLE, 0.5F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    /**
     * 拿玻璃瓶右击有经验的女仆 → 换成附魔之瓶（上游 {@code GetExpBottleEvent}）。
     *
     * <p>三条细节都照抄上游：</p>
     * <ol>
     *     <li><b>经验不足 12 时不拦截这次交互</b>（返回 {@code null}），
     *         否则「拿空瓶指向经验不够的女仆」会变成「点了没反应」，而正确表现是打开界面；</li>
     *     <li><b>潜行时一次换满</b>：瓶子数取 {@code min(经验/12, 手上瓶子数)}，
     *         也就是说手上只有 1 个瓶子就只能换 1 瓶，不会凭空变出瓶子；</li>
     *     <li>附魔之瓶<b>掉在玩家脚下</b>（上游用 {@code Containers.dropItemStack}），
     *         而不是直接塞进物品栏 —— 物品栏满时也能正常拿到。</li>
     * </ol>
     *
     * @return {@code null} 表示手里不是玻璃瓶，或经验不够（交给后续逻辑继续）
     */
    @Nullable
    private InteractionResult takeXpWithBottle(Player player, ItemStack stack) {
        if (!stack.is(Items.GLASS_BOTTLE)) {
            return null;
        }
        int count = this.getExperience() / PER_BOTTLE_XP;
        if (count <= 0) {
            return null;
        }
        count = player.isShiftKeyDown() ? Math.min(count, stack.getCount()) : 1;
        int costNum = PER_BOTTLE_XP * count;
        this.setExperience(this.getExperience() - costNum);
        if (!this.level.isClientSide) {
            stack.shrink(count);
            net.minecraft.world.Containers.dropItemStack(this.level,
                    player.getX(), player.getY(), player.getZ(),
                    new ItemStack(Items.EXPERIENCE_BOTTLE, count));
            this.playSound(SoundEvents.ITEM_PICKUP, 0.2F,
                    ((this.level.random.nextFloat() - this.level.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 打开女仆界面（上游 {@code EntityMaid#openMaidGui}）。
     *
     * <p><b>为什么先显式 {@code doCloseContainer()}：</b>{@code ServerPlayer#openMenu}
     * 第一行就是「若当前不是物品栏就 {@code closeContainer()}」，而它会<b>先发关闭包</b>
     * 再关。客户端收到关闭包会 {@code setScreen(null)} → {@code grabMouse}，
     * 鼠标被拉回窗口正中央；随后打开新界面又 {@code releaseMouse}。
     * 表现就是：切页签时鼠标跳到屏幕中心。这里手动先关（不发包），再 openMenu。</p>
     */
    public boolean openMaidGui(Player player) {
        return this.openMaidGui(player, TabIndex.MAIN);
    }

    public boolean openMaidGui(Player player, int tabIndex) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) || this.isSleeping()) {
            return false;
        }
        this.getNavigation().stop();
        serverPlayer.doCloseContainer();

        final int entityId = this.getId();
        final boolean attackConfig = tabIndex == TabIndex.TASK_CONFIG
                && this.getTask() instanceof com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
        final AttackListData listSnapshot = this.attackList;
        serverPlayer.openMenu(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(net.minecraft.server.level.ServerPlayer p,
                                               net.minecraft.network.FriendlyByteBuf buf) {
                buf.writeInt(entityId);
                if (attackConfig) {
                    listSnapshot.writeToBuf(buf);
                }
            }

            @Override
            public net.minecraft.network.chat.Component getDisplayName() {
                return EntityMaid.this.getDisplayName();
            }

            @Override
            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                    int syncId, net.minecraft.world.entity.player.Inventory inventory, Player p) {
                if (tabIndex == TabIndex.TASK_CONFIG) {
                    if (attackConfig) {
                        return new com.github.tartaricacid.touhoulittlemaid.inventory.container.task.AttackTaskConfigContainer(
                                com.github.tartaricacid.touhoulittlemaid.init.InitMenu.TASK_CONFIG_ATTACK,
                                syncId, inventory, entityId, listSnapshot);
                    }
                    return new com.github.tartaricacid.touhoulittlemaid.inventory.container.task.DefaultTaskConfigContainer(
                            com.github.tartaricacid.touhoulittlemaid.init.InitMenu.TASK_CONFIG_DEFAULT,
                            syncId, inventory, entityId);
                }
                if (tabIndex == TabIndex.MAID_CONFIG) {
                    return new com.github.tartaricacid.touhoulittlemaid.inventory.container.MaidConfigContainer(
                            com.github.tartaricacid.touhoulittlemaid.init.InitMenu.MAID_CONFIG,
                            syncId, inventory, entityId);
                }
                return new com.github.tartaricacid.touhoulittlemaid.inventory.container.MaidMainContainer(
                        com.github.tartaricacid.touhoulittlemaid.init.InitMenu.MAID_MAIN,
                        syncId, inventory, entityId);
            }
        });
        return true;
    }

    // ---------------------------------------------------------------- 通用小工具

    /**
     * 解析资源位置，非法值返回 {@code null}（存档里的 id 可能是旧版或手改的）。
     */
    @Nullable
    private static ResourceLocation parseId(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(value);
    }

    /**
     * 把一串物品 id 变成判定谓词（上游用 Ingredient，这里按注册名精确匹配）。
     */
    private static Predicate<ItemStack> matchItemIds(List<String> ids) {
        return stack -> {
            if (stack.isEmpty() || ids.isEmpty()) {
                return false;
            }
            ResourceLocation key = net.minecraft.core.Registry.ITEM.getKey(stack.getItem());
            return key != null && ids.contains(key.toString());
        };
    }

    /**
     * 非居家模式下的默认工作范围半径（读配置，改动配置后无需重建实体）。
     */
    private static float defaultRestrictRadius() {
        return (float) com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig.nonHomeRange();
    }

    /**
     * 行为树该以哪里为中心搜索目标：居家模式用工作中心，否则用自身位置。
     */
    public BlockPos getBrainSearchPos() {
        return this.hasRestriction() ? this.getRestrictCenter() : this.blockPosition();
    }

    // ---------------------------------------------------------------- 睡觉与床 / 胶片 / 替身地藏

    /**
     * 躺到女仆床上。逐字移植自上游 {@code EntityMaid#startSleeping}。
     *
     * <p><b>1.18.2 需要额外做一步：</b>Forge 的补丁把 {@code Block#isBed} 塞进了
     * {@code LivingEntity#startSleeping}，原版只认 {@code instanceof BedBlock}。
     * 女仆床不是 {@code BedBlock} 的子类，所以「占床」得自己做 ——
     * 否则床永远不显示被占用，别的女仆会一直往上爬。</p>
     */
    @Override
    public void startSleeping(BlockPos pos) {
        super.startSleeping(pos);
        com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed.setBedOccupied(this.level, pos, true);
        // 睡一觉直接回满血，好感度按 Type.SLEEP 加一次（冷却 24000 tick）
        this.setHealth(this.getMaxHealth());
        this.favorabilityManager.apply(com.github.tartaricacid.touhoulittlemaid.entity.favorability.Type.SLEEP);
        if (this.getOwner() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            com.github.tartaricacid.touhoulittlemaid.init.InitTrigger.MAID_EVENT.trigger(serverPlayer,
                    com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType.MAID_SLEEP);
        }
    }

    /**
     * 起床：把床的 {@code OCCUPIED} 清掉。
     *
     * <p>同样是 1.18.2 要自己做的那一步，见 {@link #startSleeping}。必须先把
     * {@code getSleepingPos()} 存下来 —— {@code super.stopSleeping()} 会把它清空。</p>
     */
    @Override
    public void stopSleeping() {
        Optional<BlockPos> sleepingPos = this.getSleepingPos();
        super.stopSleeping();
        sleepingPos.ifPresent(pos ->
                com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed.setBedOccupied(this.level, pos, false));
    }
    /**
     * 睡觉时头朝哪边 —— 返回女仆床的 {@code FACING}。
     *
     * <h2>这是本移植版唯一一处「故意与上游不同」的渲染钩子</h2>
     * <p>上游没有覆写这个方法，而 Forge 的默认实现只认 {@code BedBlock}，
     * 所以上游女仆床的朝向查询恒为 {@code null}。后果有两个：</p>
     * <ol>
     *     <li>{@code LivingEntityRenderer#setupRotations} 不会做那一步
     *         {@code -bedFacing * (getEyeHeight(STANDING) - 0.1)} 的水平位移，
     *         于是女仆躺下后会越过床头约一格；</li>
     *     <li>躺姿的朝向退化成 {@code 180 - bodyYaw}，也就是「她走到床边时朝哪，
     *         就斜着往哪躺」，与床的方向无关。</li>
     * </ol>
     *
     * <p>上游因为第 2 条看起来还过得去（她会大致朝着床走），但第 1 条一直存在。
     * 这里返回真实朝向，让上面两件事都按「正常睡在床上的生物」来算，
     * 同时 {@code GeoReplacedEntityRenderer#render} 里那份重复的位移已经删除，
     * 保证位移只发生一次。</p>
     */
    @Override
    public Direction getBedOrientation() {
        Optional<BlockPos> sleepingPos = this.getSleepingPos();
        if (sleepingPos.isPresent()) {
            Direction facing = com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed
                    .getMaidBedFacing(this.level, sleepingPos.get());
            if (facing != null) {
                return facing;
            }
        }
        return super.getBedOrientation();
    }


    /**
     * 是否已经生成过墓碑。
     *
     * <p>必须记着：女仆有两条走向死亡的路径 —— 正常的 {@code die()}（期间原版会调
     * {@link #dropEquipment()}），以及 {@code /kill} 直接 {@code remove(RemovalReason.KILLED)}
     * 根本不会走 {@code die()}（见 {@link #remove}）。两边都要能触发，但不许触发两次。</p>
     */
    private boolean alreadyDropped;

    /**
     * 死亡时把遗物收进墓碑，逐字移植自上游 {@code EntityMaid#dropEquipment}。
     *
     * <p>原版 {@code LivingEntity#die()} 会在 {@code dropAllDeathLoot} 里调这个方法，
     * 所以覆写它就是唯一的正确接入口（比在 {@code die()} 里手动搬运更可靠 ——
     * {@code /kill}、掉出世界等路径也都能覆盖到）。</p>
     *
     * <h2>收进去的东西</h2>
     * <ul>
     *     <li>护甲 / 主副手 / 物品栏 / 饰品栏 / 隐藏栏：合成一个 {@code CombinedInvWrapper} 逐个抽干；</li>
     *     <li>背包本身（{@code getTakeOffItemStack}）以及背包可能持有的额外数据；</li>
     *     <li>胶片刻录（{@link com.github.tartaricacid.touhoulittlemaid.item.ItemFilm#maidToFilm}）——
     *         这是神社复活唯一的凭据，必须进墓碑，否则掉出去被岩浆烧掉就永久丢失。</li>
     * </ul>
     *
     * <p><b>与上游的差异：</b>上游还会判 {@code PetBedDrop.hasPetBedPos(this)}（第三方
     * 「宠物床」模组的兼容，装了那个模组时死亡不进墓碑而是回到床上）。本移植版没有该模组，
     * 直接省略。</p>
     */
    @Override
    protected void dropEquipment() {
        if (this.getOwnerUUID() != null && !this.level.isClientSide && !this.alreadyDropped) {
            // 掉出世界的判断：把墓碑按「可站立的 y」夹到世界高度里，
            // 否则在虚空 / 基岩里死亡会生成一个永远摸不到的墓碑
            net.minecraft.world.phys.Vec3 position =
                    net.minecraft.world.phys.Vec3.atBottomCenterOf(this.blockPosition());
            if (this.getY() < this.level.getMinBuildHeight() + 5) {
                position = new net.minecraft.world.phys.Vec3(position.x, this.level.getMinBuildHeight() + 5, position.z);
            }
            if (this.getY() > this.level.getMaxBuildHeight()) {
                position = new net.minecraft.world.phys.Vec3(position.x, this.level.getMaxBuildHeight(), position.z);
            }
            com.github.tartaricacid.touhoulittlemaid.entity.item.EntityTombstone tombstone =
                    new com.github.tartaricacid.touhoulittlemaid.entity.item.EntityTombstone(
                            this.level, this.getOwnerUUID(), position);
            tombstone.setMaidName(this.getDisplayName());

            CombinedInvWrapper invWrapper = new CombinedInvWrapper(
                    this.armorInvWrapper, this.handsInvWrapper, this.maidInv, this.maidBauble, this.hideInv);
            for (int i = 0; i < invWrapper.getSlots(); i++) {
                int size = invWrapper.getSlotLimit(i);
                tombstone.insertItem(invWrapper.extractItem(i, size, false));
            }
            // 背包额外数据
            IMaidBackpack maidBackpack = this.getMaidBackpackType();
            tombstone.insertItem(maidBackpack.getTakeOffItemStack(ItemStack.EMPTY, null, this));
            maidBackpack.onSpawnTombstone(this, tombstone);
            // 胶片
            tombstone.insertItem(com.github.tartaricacid.touhoulittlemaid.item.ItemFilm.maidToFilm(this));
            // 全局记录（狐狸卷轴按玩家查墓碑用）
            com.github.tartaricacid.touhoulittlemaid.world.data.MaidWorldData maidWorldData =
                    com.github.tartaricacid.touhoulittlemaid.world.data.MaidWorldData.get(this.level);
            if (maidWorldData != null) {
                maidWorldData.addTombstones(this, tombstone);
            }

            // 记录墓碑已经生成，避免重复生成
            this.alreadyDropped = true;
            this.level.addFreshEntity(tombstone);
        }
    }

    /**
     * 被 {@code /kill} 或其它「直接移除」路径干掉时补一次墓碑。
     *
     * <p>上游为此单独写了一个 {@code remove} 覆写，注释里写着「尝试修复可能存在的目标生成
     * 丢失问题」：{@code RemovalReason.KILLED} 不会经过 {@code die()}，所以不会调
     * {@link #dropEquipment()}。</p>
     */
    @Override
    public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) {
        if (reason == net.minecraft.world.entity.Entity.RemovalReason.KILLED
                && !this.alreadyDropped && !this.level.isClientSide) {
            this.dropEquipment();
        }
        super.remove(reason);
    }


    /**
     * 替身地藏：给自己的女仆永久无敌（上游 {@code ItemSubstituteJizo} 里的
     * {@code InteractMaidEvent} 处理器）。
     *
     * <p>判断条件与上游逐字一致：手里是地藏，且她还没无敌。物品<b>无条件</b>消耗
     * （上游也没有判创造模式）。</p>
     *
     * @return {@code null} 表示手里不是地藏，或者她已经无敌了
     */
    @Nullable
    private InteractionResult applySubstituteJizo(Player player, ItemStack stack) {
        if (stack.getItem() != com.github.tartaricacid.touhoulittlemaid.init.InitItems.SUBSTITUTE_JIZO) {
            return null;
        }
        if (this.isInvulnerable()) {
            return null;
        }
        this.setInvulnerable(true);
        stack.shrink(1);
        return InteractionResult.sidedSuccess(this.level.isClientSide);
    }


    /**
     * 幸运值（钓鱼用）。1.18.2 的 createAttributes 没加 LUCK，取不到时按 0 处理。
     */
    public float getLuck() {
        var attribute = this.getAttribute(Attributes.LUCK);
        return attribute == null ? 0.0F : (float) attribute.getValue();
    }

    /**
     * 该物品能不能进女仆背包（上游 {@code canInsertItem}）。
     */
    public static boolean canInsertItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation key = net.minecraft.core.Registry.ITEM.getKey(stack.getItem());
        if (key != null && com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig
                .backpackBlacklist().contains(key.toString())) {
            return false;
        }
        return stack.getItem().canFitInsideContainerItems();
    }

    /**
     * 拾取地上的物品（上游 {@code EntityMaid#pickupItem}）。
     *
     * @param simulate 只试塞不真塞（用于判定「装得下吗」）
     */
    public boolean pickupItem(ItemEntity entityItem, boolean simulate) {
        if (this.level.isClientSide || !entityItem.isAlive() || entityItem.hasPickUpDelay()) {
            return false;
        }
        ItemStack itemstack = entityItem.getItem();
        if (!canInsertItem(itemstack)) {
            return false;
        }
        int count = itemstack.getCount();
        itemstack = ItemHandlerHelper.insertItemStacked(this.getAvailableInv(false), itemstack, simulate);
        if (count == itemstack.getCount()) {
            return false;
        }
        if (!simulate) {
            // take() 会让客户端播放短暂的拾取动画
            this.take(entityItem, count - itemstack.getCount());
            this.playPickupSound();
            if (itemstack.isEmpty()) {
                entityItem.discard();
            } else {
                entityItem.setItem(itemstack);
            }
        }
        return true;
    }

    /**
     * 每捡 5 次物品播一次「捡到东西」的语音（上游同一套节流）。
     */
    private void playPickupSound() {
        if (this.level.isClientSide) {
            return;
        }
        this.pickupSoundCount--;
        if (this.pickupSoundCount <= 0) {
            this.playMaidVoice(InitSounds.MAID_ITEM_GET, 1.0F, 1.0F);
            this.pickupSoundCount = 5;
        }
    }

    // ---------------------------------------------------------------- 方块交互（农活 / 插火把用）

    public boolean canDestroyBlock(BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = this.level.getBlockState(pos);
        return !state.isAir() && state.getDestroySpeed(this.level, pos) >= 0.0F;
    }

    public boolean canPlaceBlock(BlockPos pos) {
        return this.level.getBlockState(pos).getMaterial().isReplaceable();
    }

    public boolean destroyBlock(BlockPos pos) {
        return this.destroyBlock(pos, true);
    }

    public boolean destroyBlock(BlockPos pos, boolean dropBlock) {
        return this.canDestroyBlock(pos) && this.destroyBlock(this.level, pos, dropBlock, this);
    }

    public boolean destroyBlock(Level level, BlockPos blockPos, boolean dropBlock, @Nullable Entity entity) {
        net.minecraft.world.level.block.state.BlockState blockState = level.getBlockState(blockPos);
        if (blockState.isAir()) {
            return false;
        }
        FluidState fluidState = level.getFluidState(blockPos);
        if (!(blockState.getBlock() instanceof BaseFireBlock)) {
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, blockPos, Block.getId(blockState));
        }
        if (dropBlock) {
            BlockEntity blockEntity = blockState.hasBlockEntity() ? level.getBlockEntity(blockPos) : null;
            this.dropResourcesToMaidInv(blockState, level, blockPos, blockEntity, this, ItemStack.EMPTY);
        }
        boolean result = level.setBlock(blockPos, fluidState.createLegacyBlock(), Block.UPDATE_ALL);
        if (result) {
            level.gameEvent(entity, GameEvent.BLOCK_DESTROY, blockPos);
        }
        return result;
    }

    /**
     * 挖方块进度（对应原版 {@code Player#getDestroyProgress}）。
     *
     * <p>原版那个方法是 {@code Player} 上的，女仆不是玩家，只能自己算一遍。
     * 差异：原版会按「能否正确收获」把速度除以 30 / 100，这里统一按 30 处理 ——
     * 农活任务只拿它和阈值比较、按 tick 累加，差一个常数倍只影响快慢，不影响正确性。</p>
     */
    public float getDestroyProgress(net.minecraft.world.level.block.state.BlockState state, @Nullable BlockPos pos) {
        if (pos == null) {
            return 0.0F;
        }
        float hardness = state.getDestroySpeed(this.level, pos);
        if (hardness < 0.0F) {
            return 0.0F;
        }
        float speed = this.getMainHandItem().getDestroySpeed(state);
        return speed / hardness / 30.0F;
    }

    /**
     * 掉落物直接进女仆背包，塞不下的才丢地上（上游 {@code dropResourcesToMaidInv}）。
     */
    public void dropResourcesToMaidInv(net.minecraft.world.level.block.state.BlockState state, Level level,
                                       BlockPos pos, @Nullable BlockEntity blockEntity, EntityMaid maid,
                                       ItemStack tool) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        CombinedInvWrapper availableInv = this.getAvailableInv(false);
        Block.getDrops(state, serverLevel, pos, blockEntity, maid, tool).forEach(stack -> {
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(availableInv, stack, false);
            if (!remainder.isEmpty()) {
                Block.popResource(level, pos, remainder);
            }
        });
        state.spawnAfterBreak(serverLevel, pos, tool);
    }

    public boolean placeItemBlock(InteractionHand hand, BlockPos placePos,
                                  Direction direction, ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        BlockHitResult hit = new BlockHitResult(
                new Vec3(placePos.getX() + 0.5D + direction.getStepX() * 0.5D,
                        placePos.getY() + 0.5D + direction.getStepY() * 0.5D,
                        placePos.getZ() + 0.5D + direction.getStepZ() * 0.5D),
                direction, placePos, false);
        return blockItem.place(new com.github.tartaricacid.touhoulittlemaid.util.MaidBlockPlaceContext(
                this.level, null, hand, stack, hit)).consumesAction();
    }

    public boolean placeItemBlock(BlockPos placePos, Direction direction, ItemStack stack) {
        return this.placeItemBlock(InteractionHand.MAIN_HAND, placePos, direction, stack);
    }

    public boolean placeItemBlock(BlockPos placePos, ItemStack stack) {
        return this.placeItemBlock(placePos, Direction.UP, stack);
    }

    // ---------------------------------------------------------------- 移动

    /**
     * 主动爬梯（上游 {@code MaidClimbTask} 的等价实现）。
     *
     * <p>原版只有 {@code Player} 在 {@code aiStep} 里处理攀爬，所有 {@code Mob} 都不会爬梯子。
     * 上游为此打了 Forge 补丁并外加一个行为；这里改成在 {@link #travel} 里补一个向上的速度。</p>
     */
    private void applyClimbMotion() {
        if (!MaidSubConfig.isActiveClimbing(this) || !this.onClimbable()) {
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        if (this.horizontalCollision || this.jumping) {
            this.setDeltaMovement(motion.x, 0.2D, motion.z);
            this.fallDistance = 0.0F;
        } else if (motion.y < 0.0D) {
            // 贴着梯子不下坠
            this.setDeltaMovement(motion.x, 0.0D, motion.z);
            this.fallDistance = 0.0F;
        }
    }

    @Override
    public void travel(Vec3 travelVector) {
        // 被抱着时位置完全由玩家决定（见 updateCarriedPosition / positionRider），
        // 自身再移动就会和它抢位置 —— 表现就是「抱着走路时被女仆往前顶」。
        if (this.isCarried()) {
            super.travel(Vec3.ZERO);
            return;
        }
        // 坐姿下不接受水平移动，与坐着的小狼一致
        if (this.isMaidInSittingPose() && this.isEffectiveAi() && !this.isInWaterOrBubble()) {
            super.travel(new Vec3(0.0D, travelVector.y, 0.0D));
            return;
        }
        this.applyClimbMotion();
        super.travel(travelVector);
    }

}

