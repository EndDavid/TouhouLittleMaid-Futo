package com.github.tartaricacid.touhoulittlemaid.client.entity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.AnimationManager;
import com.github.tartaricacid.touhoulittlemaid.client.animation.inner.InnerAnimations;
import com.github.tartaricacid.touhoulittlemaid.client.animation.inner.MaidInnerAnimation;
import com.github.tartaricacid.touhoulittlemaid.client.animation.inner.MaidInnerContext;
import com.github.tartaricacid.touhoulittlemaid.client.resource.MaidModelRegistry;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimatableEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.controller.AnimationController;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.context.AnimationContext;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.processor.IBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.model.provider.data.EntityModelData;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import com.mojang.math.Vector3f;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 女仆的 GeckoLib3 动画实例。
 *
 * <p>控制器与动画选择逻辑与上游一致：主控制器交给 {@link AnimationManager} 按
 * {@link com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.AnimationRegister}
 * 注册的状态表（死亡 / 睡觉 / 游泳 / 爬梯 / 载具 / 坐下 / 受击 / 跳跃 / 跑 / 走 / 待机）
 * 优先级匹配；另外还注册持物、挥击、使用、护甲、乘客与并行层控制器。</p>
 *
 * <p><b>实例必须按实体缓存</b>：渲染器每帧都会取一次动画实例，如果每次 {@code new}，
 * 动画进度会永远停在 0，表现为「模型不播放动画、移动时像在平移」。上游靠 Forge capability
 * 缓存，这里按实体 id 缓存并校验实例身份（见 {@link #getOrCreate}）。</p>
 */
public class GeckoMaidEntity<T extends Mob> extends AnimatableEntity<T> implements IGeoEntity {
    private static final int FPS = 60;
    private static final ResourceLocation EMPTY_TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/entity/empty.png");

    /**
     * 每个实体一份动画实例。
     *
     * <p><b>这里不能用 {@code Map<Mob, ...>} / {@code WeakHashMap<Mob, ...>}：</b>
     * 1.18.2 的 {@code Entity#equals/hashCode} 是<b>按实体 id</b>实现的
     * （{@code hashCode()} 直接返回 {@code id}，{@code equals} 比较 {@code id}）。
     * 区块被卸载再载入后，客户端会用<b>同一个 id 重建一个新的</b> {@code EntityMaid} 实例，
     * 于是实体作为 key 会把旧实例的动画对象「认领」给新实体。</p>
     *
     * <p>而旧实例已经不再 tick：{@code entity.tickCount} 冻结 → {@code seekTime} 不再增长 →
     * {@link com.github.tartaricacid.touhoulittlemaid.geckolib3.core.util.RateLimiter} 永远拒绝 →
     * {@code AnimationProcessor#tickAnimation} 不再执行 → <b>动画谓词再也不被求值</b>，
     * 模型就定格在重载前的最后一帧（表现为「一直是坐下姿势」「坐着平移」）。</p>
     *
     * <p>因此改为按 id 索引，并在每次取用时<b>校验实例身份</b>：不是同一个对象就重建。</p>
     */
    private static final Map<Integer, GeckoMaidEntity<?>> CACHE = new HashMap<>();

    private final Vector3f headRot = new Vector3f();
    /** Inner animations declared by the current model, in declaration order. */
    private final List<MaidInnerAnimation> innerAnimations = new java.util.ArrayList<>();
    @Nullable
    private MaidModelInfo maidInfo;
    /** 模型被换掉时置位，强制下一次渲染重新求值动画谓词（上游同名字段）。 */
    private boolean modelDirty;
    private float currentTick = -1;
    private final MaidState maidState;
    /** 主控制器上一次被请求的动画名，仅用于诊断日志（避免每帧刷屏）。 */
    @Nullable
    private String lastMainAnimation;
    /** 「模型构建不出来 → 动画整帧不更新」只提示一次，避免刷屏。 */
    private boolean loggedMissingModel;

    public GeckoMaidEntity(T mob) {
        super(mob, FPS);
        this.maidState = new MaidState(mob);
        registerControllers();
    }

    // ------------------------------------------------------------------ per-entity 缓存

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Mob> GeckoMaidEntity<T> getOrCreate(T mob) {
        GeckoMaidEntity<?> cached = CACHE.get(mob.getId());
        // id 相同但实例不同 = 区块重载后的新实体，旧缓存对象必须丢弃（见 CACHE 注释）
        if (cached != null && cached.entity != mob) {
            cached = null;
        }
        if (cached == null) {
            cached = new GeckoMaidEntity(mob);
            CACHE.put(mob.getId(), cached);
        }
        return (GeckoMaidEntity<T>) cached;
    }

    @Nullable
    public static GeckoMaidEntity<?> peek(Mob mob) {
        GeckoMaidEntity<?> cached = CACHE.get(mob.getId());
        return cached != null && cached.entity == mob ? cached : null;
    }

    /** 实体卸载时丢弃对应缓存，避免长期堆积。 */
    public static void remove(Mob mob) {
        CACHE.remove(mob.getId());
    }

    // ------------------------------------------------------------------ 控制器

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void registerControllers() {
        AnimationManager manager = AnimationManager.getInstance();
        // parallel / pre_parallel 是 TACZ（枪械）联动专用的并行层动画。
        // 上游无条件注册，但这些控制器在 main 之后执行，会覆盖 main 写出的骨骼旋转；
        // 酒狐等模型包里恰好含有 pre_parallel0/1、parallel1/2/3，于是 sit / idle 的
        // 屈腿与摆臂会被它们整帧覆盖（表现为「坐下模型不变」）。
        // TACZ 在 Fabric 1.18.2 上不存在，因此这里只在真正加载了 TACZ 时才注册。
        boolean tacPresent = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("tacz");
        if (tacPresent) {
            for (int i = 0; i < 8; i++) {
                String controllerName = String.format("pre_parallel_%d_controller", i);
                String animationName = String.format("pre_parallel%d", i);
                addAnimationController(new AnimationController<>(this, controllerName, 0,
                        e -> manager.predicateParallel(e, animationName)));
            }
        }
        addAnimationController(new AnimationController<>(this, "main", 2, manager::predicateMain));
        addAnimationController(new AnimationController<>(this, "hold_offhand", 0, manager::predicateOffhandHold));
        addAnimationController(new AnimationController<>(this, "hold_mainhand", 0, manager::predicateMainhandHold));
        addAnimationController(new AnimationController<>(this, "swing", 2, manager::predicateSwing));
        addAnimationController(new AnimationController<>(this, "use", 2, manager::predicateUse));
        addAnimationController(new AnimationController<>(this, "misc", 2, manager::predicateMisc));
        addAnimationController(new AnimationController<>(this, "passenger", 2, manager::predicatePassengerAnimation));
        if (tacPresent) {
            for (int i = 0; i < 8; i++) {
                String controllerName = String.format("parallel_%d_controller", i);
                String animationName = String.format("parallel%d", i);
                addAnimationController(new AnimationController<>(this, controllerName, 0,
                        e -> manager.predicateParallel(e, animationName)));
            }
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ARMOR) {
                String controllerName = String.format("%s_controller", slot.getName());
                addAnimationController(new AnimationController<>(this, controllerName, 0,
                        e -> manager.predicateArmor(e, slot)));
            }
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean setCustomAnimations(AnimationContext<?> context, @NotNull AnimationEvent<?> event) {
        boolean update = super.setCustomAnimations(context, event);
        if (!update && this.getCurrentModel() == null && !this.loggedMissingModel) {
            // 此时渲染器会继续用旧骨骼姿势绘制：表现就是「模型定格在某个姿势并跟着人平移」
            this.loggedMissingModel = true;
            TouhouLittleMaid.LOGGER.warn("[Futo] animation frozen: cannot build geo model {}",
                    this.getModelLocation());
        }
        List extraData = event.getExtraData();
        AnimatedGeoModel currentModel = this.getCurrentModel();
        if (currentModel != null && !extraData.isEmpty() && extraData.get(0) instanceof EntityModelData data) {
            updateHead(data, currentModel, update);
        }
        if (currentModel != null && isLegacyModel()) {
            updateWing(event, currentModel);
            // 内建动画（上游 InnerAnimation）：按模型 animation 字段声明的顺序求值。
            // 必须放在 updateHead / updateWing 之后 —— 上游旧版模型的头部朝向、
            // 眨眼、尾巴、翅膀都是这些内建动画管的，后写者胜出才与上游一致。
            applyInnerAnimations(event, currentModel);
        }
        return update;
    }

    /**
     * 依次执行模型声明的内建动画。
     *
     * <p>这些动画来自 {@code maid_model.json} 的 {@code animation} 数组，形如
     * {@code animation/maid/default/head/default.js}。那串 {@code .js} 在上游 jar 里
     * <b>并不存在</b>，是 InnerAnimation 用同名 Java 实现顶替的伪路径；
     * 本移植版由 InnerAnimations 提供同样的实现。</p>
     *
     * <p><b>为什么不能把它们当资源读：</b>先前版本正是这么做的 —— 每条都走
     * readJson、找不到资源、抛异常并打印完整堆栈。一屏 55 个模型 × 每个十几条
     * 就是数百次带堆栈的 ERROR 写盘，渲染线程直接卡到「未响应」。</p>
     *
     * @param event        动画事件，提供部分刻与四肢摆动量
     * @param currentModel 已构建好的几何，内建动画直接改它的骨骼
     */
    private void applyInnerAnimations(@NotNull AnimationEvent<?> event, AnimatedGeoModel currentModel) {
        IMaid maid = this.getMaid();
        if (maid == null || this.innerAnimations.isEmpty()) {
            return;
        }
        // headPitch / netHeadYaw 在 EntityModelData 里取过反（-x），这里还原成正向，
        // 与上游 setRotationAngles 收到的实参同号
        float headPitch = 0.0F;
        float netHeadYaw = 0.0F;
        List extraData = event.getExtraData();
        if (!extraData.isEmpty() && extraData.get(0) instanceof EntityModelData data) {
            headPitch = -data.headPitch;
            netHeadYaw = -data.netHeadYaw;
        }
        MaidInnerContext ctx = new MaidInnerContext(maid, currentModel,
                event.getLimbSwing(), event.getLimbSwingAmount(),
                maid.asEntity().tickCount + event.getPartialTick(),
                netHeadYaw, headPitch);
        for (MaidInnerAnimation animation : this.innerAnimations) {
            animation.apply(ctx);
        }
    }

    /** 诊断用：主控制器上一次被请求的动画名。 */
    @Nullable
    public String getLastMainAnimation() {
        return this.lastMainAnimation;
    }

    public void setLastMainAnimation(@Nullable String animationName) {
        this.lastMainAnimation = animationName;
    }

    /**
     * 让头部随视线转动（上游同名逻辑）。
     */
    private void updateHead(EntityModelData data, AnimatedGeoModel currentModel, boolean update) {
        IBone head = currentModel.head();
        if (head == null) {
            return;
        }
        if (update) {
            this.headRot.set(head.getRotationX(), head.getRotationY(), 0);
        }
        head.setRotationX(this.headRot.x() + (float) Math.toRadians(data.headPitch));
        head.setRotationY(this.headRot.y() + (float) Math.toRadians(data.netHeadYaw));
    }

    /**
     * 让翅膀来回扇动 —— 逐字对齐上游 {@code MaidBaseAnimation#getWingDefault()}。
     *
     * <pre>
     * wingLeft .setRotateAngleY((float) (-Math.cos(ageInTicks * 0.3) * 0.2 + wingLeft .getInitRotateAngleY()));
     * wingRight.setRotateAngleY((float) ( Math.cos(ageInTicks * 0.3) * 0.2 + wingRight.getInitRotateAngleY()));
     * wingLeft / wingRight.setHidden(maid.isSleeping());
     * </pre>
     *
     * <p><b>为什么必须逐帧算，而不是写成关键帧动画：</b>这是一条
     * {@code cos(age * 0.3)} 的<b>连续曲线</b>，幅度 0.2 rad（约 ±11.5°）、周期约 21 tick。
     * 关键帧当然也能逼近它，但上游压根没这么做 —— 它在 {@code MaidBaseAnimation.init()}
     * 第 43 行把这段 Java 挂在一个<b>并不存在的伪路径</b>
     * {@code animation/maid/default/wing/default.js} 上（我核对过 1.18.2 与 1.20.1 的 jar，
     * 这个 {@code .js} 都没打包，默认模型包里的脚本只有 {@code arm.js} / {@code float.js}），
     * 也就是「用同名的 Java 实现顶替脚本」。</p>
     *
     * <p>同理它只对<b>旧版模型</b>生效：上游的 Gecko 模型走 {@code maid.animation.json}，
     * 那 91 支动画里没有任何一支摇翅膀。芙兰朵露（{@code flandre_scarlet}）与
     * 蕾米莉亚（{@code remilia_scarlet}）都是旧版模型，骨骼名就叫
     * {@code wingLeft} / {@code wingRight} —— 而且这两个名字<b>不在</b>
     * {@code GeckoModelLoader#LEGACY_BONE_ALIASES} 的重命名表里，所以取到的键与上游一致。</p>
     *
     * <p>{@code ageInTicks} 用 {@code tickCount + partialTick}，与上游渲染器里那个
     * {@code lerpedAge} 同源；{@code getInitRotateAngleY()} 对应
     * {@link IBone#getInitialSnapshot()} 的 {@code rotationValueY}
     * （旧版模型这两根骨头都没有初始旋转，所以实际是 0，但保留这一项才与上游对齐）。</p>
     */
    private void updateWing(@NotNull AnimationEvent<?> event, AnimatedGeoModel currentModel) {
        IMaid maid = this.getMaid();
        if (maid == null) {
            return;
        }
        float ageInTicks = maid.asEntity().tickCount + event.getPartialTick();
        double swing = Math.cos(ageInTicks * 0.3D) * 0.2D;
        boolean sleeping = maid.asEntity().isSleeping();
        setWingBone(currentModel, "wingLeft", -swing, sleeping);
        setWingBone(currentModel, "wingRight", swing, sleeping);
    }

    /**
     * 写一根翅膀骨骼：先叠回初始角度，再按睡眠状态决定是否隐藏。
     *
     * <p>骨头不存在时直接跳过 —— 带翅膀的模型才有这两根，绝大多数模型没有，
     * 缺骨是正常情况而不是错误。</p>
     */
    private static void setWingBone(AnimatedGeoModel model, String boneName, double swing, boolean sleeping) {
        AnimatedGeoBone bone = model.bones().get(boneName);
        if (bone == null) {
            return;
        }
        bone.setRotationY((float) (swing + bone.getInitialSnapshot().rotationValueY));
        bone.setHidden(sleeping);
    }

    // ------------------------------------------------------------------ 资源定位

    @Override
    public ResourceLocation getModelLocation() {
        MaidModelInfo info = effectiveInfo();
        if (info != null && info.getModel() != null) {
            // 不在这里做「几何是否已构建」检查：几何是惰性构建的，检查会永远为 false
            // 并导致所有旧版（1.10.0）模型被误判为不可用而回落到兜底模型。
            return info.getModelId();
        }
        return MaidModelRegistry.FALLBACK_MODEL_ID;
    }

    @Override
    public ResourceLocation getAnimationFileLocation() {
        MaidModelInfo info = effectiveInfo();
        if (info != null) {
            return info.getModelId();
        }
        return MaidModelRegistry.FALLBACK_MODEL_ID;
    }

    @Override
    public ResourceLocation getTextureLocation() {
        MaidModelInfo info = effectiveInfo();
        return info != null && info.getTexture() != null ? info.getTexture() : EMPTY_TEXTURE;
    }

    @Nullable
    private MaidModelInfo effectiveInfo() {
        if (this.maidInfo != null) {
            return this.maidInfo;
        }
        MaidModelInfo info = null;
        if (this.entity instanceof MaidModelHolder holder) {
            info = MaidModelRegistry.get(holder.getMaidModelId());
        }
        if (info == null) {
            info = MaidModelRegistry.fallback();
        }
        return info;
    }

    /**
     * 当前模型是否为「旧版」（{@code maid_model.json} 里没有 {@code "is_gecko": true}）。
     *
     * <p><b>为什么动画层需要知道这件事：</b>旧版模型的骨架是 1.10 / 1.12 时代的
     * {@code head}/{@code body}/{@code armLeft}… 命名，我们在加载时把它改名成
     * {@code Head}/{@code UpBody}/{@code LeftArm}… 以便复用默认动画集（见
     * {@code GeckoModelLoader#LEGACY_BONE_ALIASES}）。这是<b>近似</b>：旧版骨架缺少
     * Gecko 骨架里的 {@code DownBody}/{@code UpperBody}/{@code AllBody}/{@code AllHead}
     * 等中间层，因此凡是「靠上层骨骼补偿」的动画在旧版模型上都会走形。</p>
     *
     * <p>上游对旧版模型走的是 {@code MaidBaseAnimation}/{@code MaidTaskAnimation}
     * 那套硬编码 Java 动画（按小写骨骼名操作），本移植版还没有搬那套；因此对旧版模型
     * 里那些「为 Gecko 骨架编写」的手持/使用动画，需要降级处理。</p>
     */
    public boolean isLegacyModel() {
        MaidModelInfo info = effectiveInfo();
        return info == null || !info.isGeckoModel();
    }


    /**
     * 由女仆实体实现，向动画层暴露其 {@code ModelId}（避免动画层直接依赖实体类）。
     */
    public interface MaidModelHolder {
        String getMaidModelId();
    }

    @Override
    protected boolean forceUpdate(AnimationEvent<?> animationEvent) {
        float tick = (float) getCurrentTick(animationEvent);
        if (tick > this.currentTick) {
            this.currentTick = tick;
            this.maidState.updateState();
            this.modelDirty = false;
            return false;
        }
        if (this.modelDirty || !this.maidState.compareState()) {
            this.maidState.updateState();
            this.modelDirty = false;
            return true;
        }
        return false;
    }

    /** 与上游一致：头部 / 身体朝向的变化视为「状态变化」。 */
    private static final class MaidState {
        private final Mob maid;
        private float yHeadRot;
        private float yBodyRot;

        private MaidState(Mob maid) {
            this.maid = maid;
        }

        private boolean compareState() {
            return this.yHeadRot == this.maid.yHeadRot && this.yBodyRot == this.maid.yBodyRot;
        }

        private void updateState() {
            this.yHeadRot = this.maid.yHeadRot;
            this.yBodyRot = this.maid.yBodyRot;
        }
    }

    // ------------------------------------------------------------------ IGeoEntity

    @Override
    public IMaid getMaid() {
        return IMaid.convert(this.entity);
    }

    @Nullable
    @Override
    public MaidModelInfo getMaidInfo() {
        return this.maidInfo;
    }

    @Override
    public ILocationModel getGeoModel() {
        return this.getCurrentModel();
    }

    @Override
    public void setMaidInfo(MaidModelInfo info) {
        // Re-resolve when the model changes; order matters, so no dedup.
        this.innerAnimations.clear();
        if (info != null) {
            this.innerAnimations.addAll(InnerAnimations.resolve(info.getAnimation()));
        }
        if (this.maidInfo != info) {
            this.maidInfo = info;
            this.modelDirty = true;
        }
    }

    @Override
    public void setYsmModel(String modelId, String texture) {
        // Yes Steve Model 联动属于后续里程碑
    }

    @Override
    public void updateRoamingVars(Object2FloatOpenHashMap<String> roamingVars) {
        // 模型包漫游变量属于后续里程碑
    }
}
