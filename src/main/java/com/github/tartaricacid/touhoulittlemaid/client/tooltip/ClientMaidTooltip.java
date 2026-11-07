package com.github.tartaricacid.touhoulittlemaid.client.tooltip;

import com.github.tartaricacid.touhoulittlemaid.client.render.InventoryEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.resource.MaidModelRegistry;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.inventory.tooltip.ItemMaidTooltip;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 物品提示框里的「三维女仆预览」（上游 {@code client/tooltip/ClientMaidTooltip} 的移植）。
 *
 * <p>效果：鼠标悬停在照片上时，提示框顶部显示女仆名字，下面是一个
 * <b>缓慢自转</b>的女仆模型，高度 {@value #HEIGHT} 像素。这正是上游
 * {@code getTooltipImage} → {@code ItemMaidTooltip} → 本类 这条链的终点。</p>
 *
 * <h2>为什么本移植版要自己算名字</h2>
 *
 * <p>上游用 {@code ParseI18n.getI18nKey(info.getName())}。而 {@code MaidModelInfo#getName()}
 * 返回的是<b>带花括号的字符串</b>，形如
 * {@code {model.touhou_little_maid.hakurei_reimu.name}} —— 那是模型包里写的
 * 「可翻译键」，去掉花括号就是 lang 文件里的真 key。本移植版没有
 * {@code ParseI18n}，这里就地做同样的事（见 {@link #localize}）。</p>
 *
 * <h2>为什么要缓存一个 EntityMaid</h2>
 *
 * <p>{@code EntityMaid} 的构造会建 Brain、属性表、同步数据、{@code ItemStackHandler}……
 * 每帧新建一个会在打开背包时明显卡顿，而且 {@code GeckoMaidEntity} 的动画缓存以实体为键，
 * 每帧换新实体等于每帧重置动画。所以这里只创建一次并反复复用，每次渲染前重置
 * 模型 id / 自定义名（与上游 {@code EntityCacheUtil} 的思路一致）。</p>
 */
@Environment(EnvType.CLIENT)
public class ClientMaidTooltip implements ClientTooltipComponent {
    /**
     * 提示框里这一块的高度（像素）。上游同值。
     *
     * <p>它由两部分组成：顶部一行名字，下面留给模型的距离。
     * 这个值直接决定提示框被撑多高，所以不能随便改小 —— 改小会切掉模型下半身。</p>
     */
    private static final int HEIGHT = 70;
    /**
     * 模型绘制基准缩放。上游是 {@code 25 * info.getRenderItemScale()}。
     */
    private static final int BASE_SCALE = 25;
    /**
     * 自转速度：每 {@value #SPIN_PERIOD_MS} 毫秒转一圈。上游是
     * {@code (System.currentTimeMillis() / 25.0) % 360} 度，即 9 秒一圈。
     */
    private static final double SPIN_PERIOD_MS = 9000.0D;

    /**
     * 复用的「展示用女仆」，惰性创建；{@code null} 表示还没建过或需要重建。
     */
    @Nullable
    private static EntityMaid previewMaid;
    /**
     * {@link #previewMaid} 绑定的是哪个 {@code Level}。
     *
     * <p>实体不能跨世界复用（{@code Entity#level} 是 final 字段），
     * 退出存档再进另一个存档时必须重建，否则渲染器会去旧世界里查区块。</p>
     */
    @Nullable
    private static Level previewLevel;

    private final MaidModelInfo info;
    private final String customName;

    public ClientMaidTooltip(ItemMaidTooltip tooltip) {
        this.info = MaidModelRegistry.get(tooltip.modelId());
        this.customName = tooltip.customName();
    }

    /**
     * 把 {@code {key}} 形式的「可翻译键」还原成真正的 lang key。
     *
     * <p>上游对应 {@code ParseI18n#getI18nKey}。这里只处理最常见的一种：
     * 整串被一对花括号包住。不是这个形状就原样返回，避免误伤。</p>
     */
    private static String localize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (raw.length() > 2 && raw.charAt(0) == '{' && raw.charAt(raw.length() - 1) == '}') {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    /**
     * 顶部显示的名字组件：优先自定义名，其次模型名。
     */
    private MutableComponent displayName() {
        Component parsed = parseCustomName(this.customName);
        if (parsed != null) {
            return (MutableComponent) parsed;
        }
        if (this.info == null) {
            return new TranslatableComponent("");
        }
        return new TranslatableComponent(localize(this.info.getName()));
    }

    /**
     * 自定义名存的是 <b>JSON 文本</b>（{@code Entity#saveWithoutId} 写的是
     * {@code Component.Serializer.toJson(...)} 的结果），不是纯字符串，必须反序列化。
     */
    @Nullable
    private static Component parseCustomName(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return Component.Serializer.fromJson(json);
        } catch (Exception ignored) {
            // 存档里可能是被人手动改坏的 JSON —— 退回「没有自定义名」，不要崩
            return null;
        }
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        // 至少 50 像素，否则名字很短时模型会被横向裁掉（上游同款下限）
        return Math.max(font.width(this.displayName()), 50);
    }

    @Override
    public void renderImage(Font font, int x, int y, PoseStack poseStack, ItemRenderer itemRenderer, int blitOffset) {
        if (this.info == null) {
            // 模型没加载出来（资源重载中 / 模型包缺失），只画名字，不画模型
            font.draw(poseStack, this.displayName().withStyle(ChatFormatting.GRAY), x, y + 2, 0xFFFFFF);
            return;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        // 第一行：名字
        poseStack.pushPose();
        poseStack.translate(0, 0, blitOffset);
        font.draw(poseStack, this.displayName().withStyle(ChatFormatting.GRAY), x, y + 2, 0xFFFFFF);
        poseStack.popPose();

        EntityMaid maid = obtainPreviewMaid(level);
        if (maid == null) {
            return;
        }
        // 每次渲染前重置：模型可能换了，名字也可能换了
        maid.setModelId(this.info.getModelId().toString());
        maid.setCustomName(parseCustomName(this.customName));

        // 只画模型那一块：用 scissor 裁掉溢出提示框的部分，
        // 否则缩放较大时模型会盖住相邻的提示框文字。
        // 坐标全部用「帧缓像素」：屏幕左下角为原点，因此 Y 要用屏幕高度减。
        // （与上游 {@code ClientMaidTooltip} 里那段 scissor 公式一致）
        int width = this.getWidth(font);
        Window window = Minecraft.getInstance().getWindow();
        double guiScale = window.getGuiScale();
        int bottomY = y + HEIGHT - 6;
        int scissorX = (int) (x * guiScale);
        int scissorY = (int) (window.getHeight() - bottomY * guiScale);
        int scissorW = (int) (width * guiScale);
        int scissorH = (int) ((HEIGHT - 18) * guiScale);
        boolean scissor = scissorW > 0 && scissorH > 0;
        if (scissor) {
            RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);
        }

        // 绕 Y 轴匀速自转；再叠一个 ZP 180° 让模型正面对着我们（上游同款）
        double degrees = (System.currentTimeMillis() % (long) SPIN_PERIOD_MS) / SPIN_PERIOD_MS * 360.0D;
        Quaternion rotation = Vector3f.ZP.rotation((float) Math.PI);
        rotation.mul(Vector3f.YP.rotation((float) Math.toRadians(degrees)));

        int centerX = x + width / 2;
        InventoryEntityRenderer.render(poseStack, centerX, bottomY, blitOffset,
                (int) (BASE_SCALE * this.info.getRenderItemScale()), rotation, maid);

        if (scissor) {
            RenderSystem.disableScissor();
        }
    }

    /**
     * 拿到（或创建）那个复用的展示用女仆。
     *
     * @return 世界还没准备好时返回 {@code null}
     */
    @Nullable
    private static EntityMaid obtainPreviewMaid(Level level) {
        if (previewMaid != null && previewLevel == level) {
            return previewMaid;
        }
        EntityMaid created = InitEntities.MAID.create(level);
        if (created == null) {
            return null;
        }
        // 不让它参与任何游戏逻辑：静止、不燃烧、不被判定为「已驯服」
        created.setNoGravity(true);
        created.setInvulnerable(true);
        created.setInvisible(false);
        previewMaid = created;
        previewLevel = level;
        return created;
    }
}

