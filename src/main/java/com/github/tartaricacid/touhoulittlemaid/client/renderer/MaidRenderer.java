package com.github.tartaricacid.touhoulittlemaid.client.renderer;

import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.geckolayer.GeckoLayerMaidHeld;
import com.github.tartaricacid.touhoulittlemaid.client.resource.MaidModelRegistry;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.processor.IBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoReplacedEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 女仆渲染器：用模组自带的基岩版（GeckoLib3）模型绘制女仆。
 */
public class MaidRenderer extends GeoReplacedEntityRenderer<EntityMaid, GeckoMaidEntity<EntityMaid>>
        implements IGeoEntityRenderer<EntityMaid> {
    /**
     * 归为「闭眼」的骨骼名。
     *
     * <p>不同年代 / 不同作者的模型用了 4 种拼法（{@code blink}、{@code blink2}，
     * 以及两处明显的拼写错误 {@code _blink} / {@code _bink}，后者见上游
     * {@code MaidExtraAnimation$5} 的字符串常量）。全部列出，缺失的自动跳过。</p>
     *
     * <p><b>贴片是真能用的美术：</b>以琪露诺贴图为例，{@code blink} 的正面 UV
     * {@code (45,10)-(53,18)} 里确实画了闭眼（白 + 蓝灰 + 粉色腮红），
     * 与头部正面的 {@code (8,20)-(16,28)}（整片 {@code 7e8dc4} 刘海色）完全不同。</p>
     */
    private static final String[] BLINK_BONES = {"blink", "blink2", "_blink", "_bink"};

    /**
     * 眨眼窗口：上游 {@code MaidBaseAnimation$2} 里是 {@code 55 < remainder < 60}，
     * 周期 60 tick。等价于每 3 秒闭眼 0.25 秒。
     */
    private static final float BLINK_START = 55.0F;
    private static final float BLINK_END = 60.0F;

    /**
     * 受伤表情贴片。上游 {@code MaidExtraAnimation$4} 里 {@code hidden = !onHurt()}，
     * 而 {@code onHurt()} 就是 {@code hurtTime > 0}。
     */
    private static final String HURT_BONE = "hurtBlink";

    /**
     * 旧版模型被抱着时的额外位移（格），来自上游 MaidBaseAnimation#ridingPosture
     * 末尾的 {@code GlWrapper.translate(0, 0.3, 0)}。
     *
     * <p>方向是<b>向下、并带一个水平分量</b>：它作用在已被 Y 翻转的模型空间里
     * （{@code setupAnim} 排在 {@code scale(-1,-1,1)} 之后），所以等价于 y-up 系里的
     * -0.3，再被 Rz(-65) 转成约 (-0.272, -0.127, 0)。常量保留 0.3 这个上游原值，
     * 符号在调用处取负。</p>
     */
    private static final double LEGACY_RIDING_LIFT = 0.3D;

    /**
     * 表情贴片沿模型前方向外推的距离（单位：模型单位，1 单位 = 1/16 方块）。
     */

    public MaidRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
        // 手持物品层：数值全部照搬上游（见 GeckoLayerMaidHeld 的类注释）
        this.addLayer(new GeckoLayerMaidHeld<>(this));
    }

    @Override
    public GeckoMaidEntity<EntityMaid> getAnimatableEntity(EntityMaid entity) {
        // 必须复用 per-entity 缓存，否则动画进度每帧归零（表现为模型「平移」）
        GeckoMaidEntity<EntityMaid> animatable = GeckoMaidEntity.getOrCreate(entity);
        animatable.setMaidInfo(resolveInfo(entity));
        return animatable;
    }

    /**
     * 在模型真正绘制之前调整骨骼可见性 —— 这是唯一能保证「本帧生效」的时机。
     */
    /**
     * 女仆本体绘制完成之后，再画头顶的聊天气泡。
     *
     * <h2>为什么覆写 render 而不是加一个 LayerRenderer</h2>
     * <p>气泡要画在「世界坐标 + 相机朝向」上，不是实体模型空间；</p>
     * <p>而 super.render 结束时 PoseStack 已经弹回「实体脚部为原点」，
     * 正好是气泡需要的坐标系。加图层反而要多做一次矩阵还原。</p>
     *
     * <h2>只给真正的女仆画</h2>
     * <p>雕像与手办把实体 NBT 读进一个临时实体来渲染，那些实例的 renderState
     * 不是 ENTITY。给它们画气泡既没有意义，</p>
     * <p>也会让「摆在家里当装饰」的手办冒出 15 秒就消失的空气泡。</p>
     */
    @Override
    public void render(EntityMaid entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        if (entity.renderState
                != com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState.ENTITY) {
            return;
        }
        // 气泡显示开关：全局配置 + 这只女仆自己的开关（上游 1.20.1 EntityMaidRenderer 同一处判断）。
        // 两个都要看 —— 全局关掉是「整个存档都不显示」，单独关掉是「这只是太吵了」。
        if (!com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig.GLOBAL_MAID_SHOW_CHAT_BUBBLE.get()
                || !com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidSubConfig.isShowChatBubble(entity)) {
            return;
        }
        double distanceSqr = net.minecraft.client.Minecraft.getInstance()
                .getEntityRenderDispatcher().distanceToSqr(entity);
        com.github.tartaricacid.touhoulittlemaid.client.renderer.ai.ChatBubbleRenderer
                .renderChatBubble(entity, poseStack, bufferSource, packedLight, distanceSqr);
    }
    @Override
    public void renderEarly(EntityMaid animatable, PoseStack poseStack, float partialTick,
                            MultiBufferSource bufferSource, VertexConsumer buffer,
                            int packedLight, int packedOverlayIn,
                            float red, float green, float blue, float alpha) {
        super.renderEarly(animatable, poseStack, partialTick, bufferSource, buffer, packedLight,
                packedOverlayIn, red, green, blue, alpha);
        // 注意：旧版模型那个「骑着 / 抱着时多抬一点」的位移**不在这里**，
        // 它必须写在 setupRotations 里、两次 mulPose 之后，原因见那里的注释。
        this.updateOverlayBones(animatable, partialTick);
    }

    /**
     * 按上游的硬编码动画规则刷新表情贴片的可见性。
     *
     * <p>逐帧写同一个值没有副作用：{@code AnimatedGeoBone#setHidden} 只是两个布尔字段，
     * 不像动画控制器那样会被快照 / 回放覆盖。</p>
     */
    private void updateOverlayBones(EntityMaid entity, float partialTick) {
        AnimatedGeoModel model = this.getAnimatableEntity(entity).getCurrentModel();
        if (model == null) {
            return;
        }

        // 1) 闭眼贴片：上游 MaidBaseAnimation$2（对应 head/blink.js）的判定
        //
        //      if (mob.isSleeping()) { blink.setHidden(false); return; }
        //      remainder = (ageInTicks + |uuid.leastBits| % 10) % 60;
        //      blink.setHidden(!(55 < remainder && remainder < 60));
        //
        // 即：只有每 60 tick 里的最后 5 tick 显示闭眼贴片（约 3 秒眨一次，每次 0.25 秒）；
        // 睡着时一直闭眼。
        boolean blinkVisible;
        if (entity.isSleeping()) {
            blinkVisible = true;
        } else {
            float ageInTicks = entity.tickCount + partialTick;
            float remainder = (ageInTicks + Math.abs(entity.getUUID().getLeastSignificantBits()) % 10) % 60;
            blinkVisible = remainder > BLINK_START && remainder < BLINK_END;
        }
        for (String name : BLINK_BONES) {
            IBone blink = model.bones().get(name);
            if (blink != null) {
                blink.setHidden(!blinkVisible);
            }
        }

        // 2) 受伤表情：上游 MaidExtraAnimation$4（对应 head/hurt.js）
        //      hurtBlink.setHidden(!maid.onHurt());
        //    而 onHurt() 就是 hurtTime > 0。
        //    不处理的话它默认可见 —— 琪露诺一生成就是受伤脸。
        IBone hurt = model.bones().get(HURT_BONE);
        if (hurt != null) {
            hurt.setHidden(entity.hurtTime <= 0);
        }


        // 3) 模型自带的护甲贴片：上游 MaidArmorAnimation#getArmorDefault
        //
        //      if (helmet != null)         helmet.setHidden(!maid.hasHelmet());
        //      if (chestPlate != null)     chestPlate.setHidden(!maid.hasChestPlate());
        //      if (chestPlateLeft != null) chestPlateLeft.setHidden(!maid.hasChestPlate());
        //      ... leggings / boots 同理
        //
        //    也就是说：没穿对应部位的护甲时，模型里自带的护甲片应当隐藏。
        updateArmorBones(entity, model);
    }

    /**
     * 隐藏「模型自带护甲片」里当前没有穿对应护甲的那些。
     *
     * <p><b>这不只是护甲问题。</b>有些模型把装饰品挂在护甲片下面当子骨骼，
     * 芙兰朵露（{@code flandre_scarlet}）就是典型：</p>
     *
     * <pre>
     * armLeft
     *   chestPlateLeft   ← 护甲片（0 cube，纯容器）
     *     bone257        ← 手握的棒子（8 cube）
     *       bow2         ← 红色缎带蝴蝶结（1 cube）
     *       bone258      ← 棒身（6 cube）
     *             wingLeft / wingRight 各自挂着 crystal1..14 的水晶链
     * </pre>
     *
     * <p>上游默认没穿护甲 → {@code chestPlateLeft} 被隐藏 → 整棵子树（棒子 + 缎带）
     * 一起不画。本移植版之前没有这条规则，于是它们一直显示；它们正好挂在左手上，
     * 第一人称抱着她时就会糊到摄像机上 —— 这才是那张「模型糊脸」截图里多出来的东西。
     * 模型本身没有问题，也没有被弃用。</p>
     *
     * <p>另有一套「反向」变体（{@code _helmet} / {@code _chestPlate} …，
     * 见上游 {@code MaidArmorAnimation#getArmorReverse}）。旧版骨骼名经过
     * {@code GeckoModelLoader} 改名时以 {@code _} 开头的名字不受影响，按原名查即可。</p>
     *
     * <p>护甲槽判定直接用 {@link EquipmentSlot} 取物品，语义与上游
     * {@code IMaid#hasChestPlate()} 那些默认方法完全一致。</p>
     */
    private static void updateArmorBones(EntityMaid entity, AnimatedGeoModel model) {
        hideUnless(model, "helmet", hasArmor(entity, EquipmentSlot.HEAD));
        hideUnless(model, "chestPlate", hasArmor(entity, EquipmentSlot.CHEST));
        hideUnless(model, "chestPlateLeft", hasArmor(entity, EquipmentSlot.CHEST));
        hideUnless(model, "chestPlateMiddle", hasArmor(entity, EquipmentSlot.CHEST));
        hideUnless(model, "chestPlateRight", hasArmor(entity, EquipmentSlot.CHEST));
        hideUnless(model, "leggings", hasArmor(entity, EquipmentSlot.LEGS));
        hideUnless(model, "leggingsLeft", hasArmor(entity, EquipmentSlot.LEGS));
        hideUnless(model, "leggingsMiddle", hasArmor(entity, EquipmentSlot.LEGS));
        hideUnless(model, "leggingsRight", hasArmor(entity, EquipmentSlot.LEGS));
        hideUnless(model, "bootsLeft", hasArmor(entity, EquipmentSlot.FEET));
        hideUnless(model, "bootsRight", hasArmor(entity, EquipmentSlot.FEET));
    }

    private static boolean hasArmor(EntityMaid entity, EquipmentSlot slot) {
        return !entity.getItemBySlot(slot).isEmpty();
    }

    /**
     * 骨头存在时按 {@code visible} 设置隐藏；不存在就跳过（多数模型没有护甲片）。
     *
     * <p>用双参重载 {@code setHidden(selfHidden, skipChildRendering)}：只隐藏这根骨头
     * 本身不够 —— 护甲片通常是 0 cube 的纯容器，要挡掉的其实是它<b>底下</b>的装饰品，
     * 所以必须连子骨骼一起跳过渲染。上游 {@code ModelRendererWrapper#setHidden}
     * 也是「整棵子树」语义。</p>
     */
    private static void hideUnless(AnimatedGeoModel model, String boneName, boolean visible) {
        AnimatedGeoBone bone = model.bones().get(boneName);
        if (bone == null) {
            return;
        }
        bone.setHidden(!visible, !visible);
    }

    /**
     * 被抱着（鞍）时的模型姿态 —— <b>横抱在玩家两手之间</b>。
     */
    protected void setupRotations(EntityMaid maid, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTicks) {
        super.setupRotations(maid, poseStack, ageInTicks, rotationYaw, partialTicks);
        boolean legacyModel = this.getAnimatableEntity(maid).isLegacyModel();
        if (!maid.isCarried()) {
            // 非「被抱着」的乘客（坐垫 / 船 / 扫帚）也要吃 ridingPosture 的那 0.3。
            //
            // 上游 MaidBaseAnimation#ridingPosture() 末尾无条件执行
            //     GlWrapper.translate(0, 0.3, 0)
            // 而它的调用条件是 mob.isPassenger() —— 坐垫、船、扫帚、被玩家抱着全都算。
            // 之前这里只在「被抱着」那条分支里补了这 0.3，于是「女仆坐坐垫比原版高 0.3 格」。
            //
            // 这里没有 Z 轴旋转（super 只做了 Ry(180 - yaw)），而 Y 方向的平移与绕 Y 的旋转
            // 可交换，所以直接平移即可；geckolib 的 RenderUtils 是
            // translate(-x/16, +y/16, +z/16)，模型空间里「向下」就是 -Y。
            if (legacyModel && maid.isPassenger()) {
                poseStack.translate(0.0D, -LEGACY_RIDING_LIFT, 0.0D);
            }
            return;
        }
        // 逐字照抄上游 client/renderer/entity/EntityMaidRenderer#setupRotations：
        //
        //     if (mob.getVehicle() instanceof Player && !mainInfo.isGeckoModel()) {
        //         poseStack.translate(-0.375, 0.8325, 0.375);
        //         poseStack.mulPose(Vector3f.ZN.rotationDegrees(65));
        //         poseStack.mulPose(Vector3f.YN.rotationDegrees(-80));
        //     }
        //
        // 三个数字原样照搬，尤其 Y 恒为 0.8325：旧版模型那点额外位移不在这一段里
        // （见下面的 LEGACY_RIDING_LIFT），所以这里不能按模型种类改数值。
        //
        // 之前写成 0.8325 - 0.3（把模型空间的那 0.3 折进 Y）是错的。那 0.3 作用在
        // 两个旋转之后，会被 Rz(-65) / Ry(80) 一起转，因此它同时带一个水平分量
        // （约 -0.272 格）。只扣 Y 就把这个水平分量丢了，人整体偏到一侧。
        //
        // 平移写在旋转前面：此刻坐标系还是实体本地系（+Y 朝上、-Z 是正前方），
        // 位移方向不会被之后的旋转带歪。
        poseStack.translate(-0.375D, 0.8325D, 0.375D);
        poseStack.mulPose(com.mojang.math.Vector3f.ZN.rotationDegrees(65));
        poseStack.mulPose(com.mojang.math.Vector3f.YP.rotationDegrees(80));
        // 旧版模型：上游 MaidBaseAnimation#ridingPosture 末尾还有一句
        //     GlWrapper.translate(0, 0.3, 0)
        //
        // 它在 setupAnim 里执行。用 javap 核对过 LivingEntityRenderer#render 的调用顺序：
        //     setupRotations -> scale(-1,-1,1) -> scale(k) -> translate(0,-1.501,0)
        //     -> prepareMobModel -> setupAnim -> renderToBuffer
        // 所以这 0.3 作用在**已经被 Y 翻转的模型空间**里，等价位移是
        //     Rz(-65) * S(-1,-1,1) * (0, 0.3, 0) = Rz(-65) * (0, -0.3, 0)
        //                                           = (-0.272, -0.127, 0)
        // 因此在这里（两次 mulPose 之后的 y-up 系）必须写 **-0.3**。
        // 写 +0.3 会把它翻成 (+0.272, +0.127, 0)，正好偏到另一侧。
        if (legacyModel) {
            poseStack.translate(0.0D, -LEGACY_RIDING_LIFT, 0.0D);
        }
    }

    @Override
    public float getWidthScale(EntityMaid entity) {
        MaidModelInfo info = resolveInfo(entity);
        return info == null ? 1.0F : info.getRenderEntityScale();
    }

    @Override
    public float getHeightScale(EntityMaid entity) {
        return this.getWidthScale(entity);
    }

    @Nullable
    private MaidModelInfo resolveInfo(EntityMaid entity) {
        MaidModelInfo info = MaidModelRegistry.get(entity.getModelId());
        // 旧版（1.10.0）模型同样走 GeckoLib 管线：加载时已把骨骼名映射成 Gecko 命名并补了
        // 合成 Root，因此这里不再区分 isGeckoModel，只要求模型文件存在。
        if (info == null || info.getModel() == null) {
            info = MaidModelRegistry.fallback();
        }
        return info;
    }

    @Override
    @NotNull
    public ResourceLocation getTextureLocation(EntityMaid entity) {
        MaidModelInfo info = resolveInfo(entity);
        if (info != null && info.getTexture() != null) {
            return info.getTexture();
        }
        return super.getTextureLocation(entity);
    }

    @Override
    public IGeoEntity getGeoEntity(EntityMaid entity) {
        return this.getAnimatableEntity(entity);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addGeoLayerRenderer(GeoLayerRenderer<?, ?> layerRenderer) {
        this.addLayer((GeoLayerRenderer<EntityMaid, ?>) layerRenderer);
    }

    @Override
    public void geoRender(EntityMaid entity, float entityYaw, float partialTick, PoseStack poseStack,
                          MultiBufferSource bufferSource, int packedLight) {
        this.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

}
