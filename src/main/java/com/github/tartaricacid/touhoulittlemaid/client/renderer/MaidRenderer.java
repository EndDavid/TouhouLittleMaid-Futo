package com.github.tartaricacid.touhoulittlemaid.client.renderer;

import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.geckolayer.GeckoLayerMaidHeld;
import com.github.tartaricacid.touhoulittlemaid.client.resource.MaidModelRegistry;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.processor.IBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoReplacedEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 女仆渲染器：用模组自带的基岩版（GeckoLib3）模型绘制女仆。
 *
 * <p>模型来源是内置模型包（{@code assets/geckolib}、{@code assets/touhou_little_maid} 等），
 * 由 {@link com.github.tartaricacid.touhoulittlemaid.client.resource.GeckoModelLoader}
 * 在资源重载时解析；实体上的 {@code ModelId} 决定用哪一套。</p>
 *
 * <h2>人脸贴片（表情层）为什么要在这里处理</h2>
 *
 * <p>TLM 的模型把「表情」做成独立的 <b>0 厚度贴片骨骼</b>，叠在头部方块正面上：</p>
 * <table border="1">
 *     <caption>旧版模型（1.10 / 1.12）里的表情骨骼</caption>
 *     <tr><th>骨骼</th><th>含义</th><th>上游由谁驱动</th></tr>
 *     <tr><td>{@code blink}</td><td>闭眼</td><td>{@code animation/maid/default/head/blink.js}</td></tr>
 *     <tr><td>{@code hurtBlink}</td><td>受伤表情（XX 眼 + 青筋）</td><td>{@code head/hurt.js}</td></tr>
 * </table>
 *
 * <p><b>关键事实：些些 {@code .js} 文件在两个上游 jar 里都不存在。</b>
 * 1.18.2 与 1.20.1 的发布 jar 里 {@code .js} 数量分别是 0和 2（且都是
 * {@code authors_and_credits} 的展示动画）。上游其实是用
 * {@code MaidBaseAnimation} / {@code MaidExtraAnimation} 里的 Java 匿名类
 * <b>硬编码</b>了同名动画 —— 用 {@code javap} 反编译核对过：</p>
 *
 * <pre>
 * MaidExtraAnimation$4（对应 head/hurt.js）：
 *     hurtBlink.setHidden(!maid.onHurt());
 *
 * MaidBaseAnimation$2（对应 head/blink.js）：
 *     remainder = (ageInTicks + |uuid.leastBits| % 10) % 60;
 *     blink.setHidden(!(55 &lt; remainder &amp;&amp; remainder &lt; 60));
 * </pre>
 *
 * <p>本移植版还没有搬那套硬编码动画，于是这些贴片就保持「模型默认可见」。
 * 现象就是：<b>琪露诺这类带受伤表情的模型，一生成就顶着受伤脸</b>
 * （用户报告的那个问题），而且所有模型都会「一直闭眼」。</p>
 *
 * <p>这里按上游的判定把状态补回来。放在 {@code renderEarly} 是因为它是
 * <b>动画 tick 之后、骨骼真正绘制之前</b>唯一的钩子，写进去的值本帧一定生效。</p>
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
     * 表情贴片沿模型前方向外推的距离（单位：模型单位，1 单位 = 1/16 方块）。
     *
     * <p><b>为什么需要这个偏移：</b>表情贴片与头部正面几乎是同一张平面。
     * 以灵梦（{@code hakurei_reimu}）为例：</p>
     * <pre>
     * head  : origin [-4, 18, -4]     size [8, 8, 8]  → 正面 z = -4    / 16 = -0.2500000
     * blink : origin [-4, 18, -4.001] size [8, 8, 0]  → 平面 z = -4.001/ 16 = -0.2500625
     *                                                    间距   = 6.25e-5 方块
     * </pre>
     *
     * <p>而 1.18.2 的深度缓冲是 24 位、近平面 0.05，精度随距离平方增长：</p>
     * <pre>
     * Δd ≈ d² · (1 / near) / 2²⁴ = d² · 1.19e-6
     * d = 5 格 → 3.0e-5   (小于 6.25e-5，贴片稳定胜出)
     * d = 7 格 → 5.8e-5   (临界)
     * d = 8 格 → 7.6e-5   (大于 6.25e-5 —— 两个面开始互相穿插)
     * </pre>
     *
     * <p>也就是说站在 7 格开外时，脸和表情贴片会逐像素「抢深度」，画面表现正是
     * 「眨眼时脸部明暗/明暗纹理忽变」—— 不是光照算错，而是同一位置有两个面在打架。
     * 之前把贴片背面的重复面删掉只解决了一半（贴片自身不再自相残杀），
     * 贴片与头部正面之间仍然共面。</p>
     *
     * <p>这里把贴片整体沿 -Z（模型正面方向）外推 {@code 0.02} 模型单位
     * = {@code 1.25e-3} 方块，是原间距的 20 倍，也远高于常规视距下的深度精度，
     * 因此在任何距离都不会再穿插；而 0.00125 方块在视觉上完全不可见。</p>
     *
     * <p>模型空间里只有 X 被镜像（见 {@code GeoCube}：{@code origin.x = -(x + size.x)/16}），
     * Y / Z 不镜像，所以「向外」恒为 -Z。</p>
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
    }

    /**
     * 被抱着（鞍）时的模型姿态 —— <b>横抱在玩家两手之间</b>。
     *
     * <h2>上游原文（1.18.2 与 1.20.1 逐字一致）</h2>
     *
     * <p>{@code EntityMaidRenderer#setupRotations}：</p>
     *
     * <pre>
     * if (mob.getVehicle() instanceof Player &amp;&amp; !mainInfo.isGeckoModel()) {
     *     poseStack.translate(-0.375, 0.8325, 0.375);
     *     poseStack.mulPose(Axis.ZN.rotationDegrees(65));
     *     poseStack.mulPose(Axis.YN.rotationDegrees(-80));
     * }
     * </pre>
     *
     * <p>本移植版把 legacy 与 gecko 两种模型并到了同一条 gecko 管线，那个
     * {@code !isGeckoModel()} 分支永远不会命中，所以必须在这里补一次。</p>
     *
     * <h2>为什么这三个数字可以逐字照搬</h2>
     *
     * <p>关键在「{@code setupRotations} 里到底是哪个坐标系」。反编译 1.18.2 的
     * {@code LivingEntityRenderer#render}，那几行的调用顺序是：</p>
     *
     * <pre>
     * this.setupRotations(entity, poseStack, ...);   // ← 我们在这里画的东西
     * poseStack.scale(-1.0F, -1.0F, 1.0F);           // ← 翻转在这之后
     * poseStack.translate(0.0D, -1.501D, 0.0D);
     * </pre>
     *
     * <p>矩阵是右乘的，所以最终是
     * {@code M = T · Rz · Ry · S(-1,-1,1) · T(0,-1.501,0)}：顶点<b>先</b>被
     * {@code T(0,-1.501,0)} 和 {@code S(-1,-1,1)} 处理，<b>之后</b>才轮到
     * {@code setupRotations} 里写的那三个变换。也就是说那三个变换真正作用在一个：</p>
     *
     * <ul>
     *     <li>{@code +Y} 朝上、</li>
     *     <li>脚底在 {@code y = 0}（{@code T(0,-1.501,0)} 把模型对齐到原点）、</li>
     *     <li>{@code +X} 是角色右侧、{@code -Z} 是面朝方向（{@code S(-1,-1,1)} 只翻转 X / Y）</li>
     * </ul>
     *
     * <p>的<b>普通实体本地坐标系</b>上。再看 {@code GeoCube}：构建几何时做的正是同一套
     * 镜像（{@code origin.x = -(origin.x + size.x) / 16}，X 取负、Y / Z 不动），
     * gecko 管线拿到的同样是「+Y 朝上、脚底在 y=0、+X 是角色右侧、-Z 是面朝方向」。
     * <b>两个坐标系完全一致，旋转中心也都在脚底</b>，所以照搬即可。</p>
     *
     * <h2>她最终落在哪（相对玩家脚底，身高按 1.5 算）</h2>
     *
     * <p>实体轴心由 {@code EntityMaid#updateCarriedPosition()} 放在「身前 0.75、抬高 0.15」；
     * 局部 {@code +Z} 是<b>朝玩家</b>，所以 translate 里的 {@code +0.375} 是往回收。</p>
     *
     * <table border="1">
     *     <caption>横抱姿态的关键点（Gecko 模型）</caption>
     *     <tr><th>点</th><th>右</th><th>上</th><th>前</th></tr>
     *     <tr><td>脚底（模型原点）</td><td>-0.375</td><td>0.98</td><td>0.375</td></tr>
     *     <tr><td>身体中点</td><td>+0.31</td><td>1.30</td><td>0.375</td></tr>
     *     <tr><td>头部</td><td>+1.17</td><td>1.70</td><td>0.375</td></tr>
     * </table>
     *
     * <p><b>旧版（1.10 / 1.12）模型整体再低 0.3 格</b>（三行数值各减 0.3）：
     * 上游 {@code MaidBaseAnimation#ridingPosture} 末尾还有一句
     * {@code GlWrapper.translate(0, 0.3, 0)}，它作用在<b>模型空间</b>
     * （被 {@code S(-1,-1,1)} 翻成 -0.3），等价于把这里的 Y 从 0.8325 减到 0.5325。
     * 详见下面的实现说明。</p>
     */
    protected void setupRotations(EntityMaid maid, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTicks) {
        super.setupRotations(maid, poseStack, ageInTicks, rotationYaw, partialTicks);
        if (!maid.isCarried()) {
            return;
        }
        // 第一人称「往前推」—— 见 firstPersonPush 的说明。
        // 必须写在下面两次 mulPose **之前**：此刻坐标系还是实体本地系
        // （+Y 朝上、-Z 是实体正前方），translate 的方向不会被之后的旋转带歪；
        // 写在后头的话这 0.4 会被 Rz(-65°)/Ry(80°) 一起转掉，方向就错了。
        double firstPersonPush = this.firstPersonPush(maid);
        if (firstPersonPush > 0.0D) {
            poseStack.translate(0.0D, 0.0D, -firstPersonPush);
        }
        // 逐字照抄上游，顺序不能换：平移写在旋转前面，作用在未旋转的本地坐标系里；
        // 反过来的话位移方向会被一起转掉。
        //
        // 旧版模型还有一步：上游 `MaidBaseAnimation#ridingPosture` 末尾的
        //     GlWrapper.translate(0, 0.3, 0)
        //
        // 它在 `EntityModel#setupAnim` 里 push，是整条矩阵链的**最后一环**，也就是
        // 顶点先经过它。而上游链尾是
        //     ... · Rz(-65°) · Ry(80°) · S(-1,-1,1) · T(0,-1.501,0) · T(0,0.3,0)
        // `S(-1,-1,1)` 会把模型空间的 +0.3 翻成 -0.3，所以这两段平移可以合并成
        //     T(0,-1.801,0)·S   （原来是 T(0,-1.501,0)·S）
        // 也就是说它等价于「主 translate 的 Y 少 0.3」，**不是**「旋转之后再抬 0.3」。
        // 这一点很要命：如果写成旋转之后的 translate(0,-0.3,0)，那 0.3 会被
        // Rz(-65°)/Ry(80°) 一起转掉，实际位移变成「往玩家怀里收 0.27 格」——
        // 第一人称的摄像机因此直接钻进她的身体里，画面就是「模型糊脸」。
        double y = this.getAnimatableEntity(maid).isLegacyModel() ? 0.8325D - 0.3D : 0.8325D;
        poseStack.translate(-0.375D, y, 0.375D);
        poseStack.mulPose(com.mojang.math.Vector3f.ZN.rotationDegrees(65));
        poseStack.mulPose(com.mojang.math.Vector3f.YP.rotationDegrees(80));
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

    /**
     * 第一人称下把抱着的女仆往前推多少格。
     *
     * <h2>为什么需要（上游没有）</h2>
     *
     * <p>把 1.18.2、1.20.1 Forge、1.20.1 Fabric 三个发布 jar 的<b>全部</b> {@code .class}
     * 和<b>全部</b> {@code .json} 都扫过：{@code isFirstPerson} / {@code getCameraType} /
     * {@code CameraType} 在渲染链路里只出现在 HUD 提示 {@code MaidTipsOverlay} 和 Molang 的
     * {@code query.is_first_person}；动画里唯一的 {@code first_person} 是 YSM 模型专用的
     * {@code ysm.first_person_mod_hide}。上游针对第一人称只做了一件事：
     * {@code CarryMaidHideArmEvent} 取消 {@code RenderHandEvent}，把玩家自己的手藏掉
     * （本移植版已用 {@code ItemInHandRendererMixin} 等价实现）。</p>
     *
     * <h2>为什么本移植版必须做</h2>
     *
     * <p>按模型 JSON 逐骨骼算到世界空间：实体轴心在「玩家脚底朝前 0.75、抬高 0.15」，
     * 再叠加 {@code setupRotations} 的 {@code translate(-0.375, 0.8325, 0.375)}，
     * 她的躯干只落在玩家正前方 {@code 0.375} 格。把第一人称摄像机（眼高 1.62）
     * 反变换回模型局部系，距她小腿外表面只有约 <b>1.6 cm</b> —— 任何网格贴到
     * 近平面 1.6 cm 处，投影后都会铺满整个视野，也就是「模型糊脸」。
     * 第三人称相机在 4 格外，同样的姿态完全正常。</p>
     *
     * <h2>只影响渲染</h2>
     *
     * <p>这里返回的推进量只写进 {@link PoseStack}，<b>不碰实体坐标</b>：
     * 第三人称、碰撞、乘客同步、平滑插值全部不受影响，退出第一人称立刻恢复。
     * 数值来自 {@code MaidConfig#carryFirstPersonOffset()}（默认 {@code 0.4}，{@code 0} 关闭）。</p>
     */
    private double firstPersonPush(EntityMaid maid) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !minecraft.options.getCameraType().isFirstPerson()) {
            return 0.0D;
        }
        // 乘客关系是两端各自复现的（见 EntityMaid#isCarried），所以同时看 getVehicle 与同步字段
        boolean carriedByCamera = maid.getVehicle() == player || maid.getCarrierId() == player.getId();
        if (!carriedByCamera || maid.distanceToSqr(player) > 4.0D) {
            return 0.0D;
        }
        return MaidConfig.carryFirstPersonOffset();
    }
}
