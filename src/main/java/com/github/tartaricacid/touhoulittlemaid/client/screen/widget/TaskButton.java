package com.github.tartaricacid.touhoulittlemaid.client.screen.widget;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 任务列表里的单个工作模式按钮，移植自上游
 * {@code client/gui/widget/button/TaskButton}。
 *
 * <p><b>1.18.2 与 Forge 上游的差异：</b>上游那里 {@code Button} 有 Forge 扩展出来的
 * 「带 tooltip 的构造函数」和 {@code OnTooltip} 回调；Fabric 的原版 {@link Button}
 * 没有这套东西。所以这里把 tooltip 列表直接作为构造参数传进来，由
 * {@link #renderTooltip} 画，逻辑等价。</p>
 *
 * <p><b>贴图约定（{@code maid_gui_task.png}，256x256）：</b></p>
 * <pre>
 * 面板底图       (0,0)   92x251
 * 上一页         (110,0) 16x13
 * 下一页         (93,0)  16x13
 * 关闭           (127,0) 13x13
 * 按钮背景       (93,28) 83x19      悬停时 V+20
 * 禁用小锁       (93,68) 7x19
 * </pre>
 */
public class TaskButton extends Button {
    /**
     * 按钮背景图在贴图里的起点。
     */
    private static final int TEX_U = 93;
    private static final int TEX_V = 28;
    /**
     * 悬停态相对常态的 V 偏移（上游 {@code yDiffTex}）。
     */
    private static final int TEX_V_HOVER = 20;
    /**
     * 禁用时盖在按钮上的半透明黑（上游 {@code 0x80000000}）。
     */
    private static final int DISABLE_MASK = 0x80000000;
    /**
     * 禁用小锁的贴图偏移与尺寸。
     */
    private static final int LOCK_U = 93;
    private static final int LOCK_V = 68;
    private static final int LOCK_W = 7;
    private static final int LOCK_H = 19;
    private static final int LOCK_X = 72;

    private final IMaidTask task;
    private final boolean enable;
    private final ResourceLocation texture;
    private final List<Component> tooltip;

    /**
     * @param x       绝对坐标（渲染层传入 {@code leftPos - 89} 之类的值）
     * @param y       绝对坐标
     * @param task    该按钮代表的工作模式
     * @param enable  {@code task.isEnable(maid)} 的结果，决定要不要画遮罩与小锁
     * @param texture 按钮贴图
     * @param tooltip 悬停提示（可为空列表，但不要传 null）
     * @param onPress 点击回调
     */
    public TaskButton(int x, int y, IMaidTask task, boolean enable, ResourceLocation texture,
                      List<Component> tooltip, OnPress onPress) {
        super(x, y, 83, 19, TextComponent.EMPTY, onPress);
        this.task = task;
        this.enable = enable;
        this.texture = texture;
        this.tooltip = tooltip;
    }

    public IMaidTask getTask() {
        return this.task;
    }

    /**
     * 禁用状态下要吞掉点击：
     *
     * <p>父类的 {@code active} 字段管不到这里 —— 上游之所以单独覆写，是因为
     * 「不可用的任务」仍然要能在列表里显示（灰色 + 小锁），但不该响应点击。
     * 用 {@code active = false} 反而会让整个按钮不再渲染悬停态。</p>
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.enable) {
            return false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer itemRenderer = minecraft.getItemRenderer();

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, this.texture);
        RenderSystem.enableDepthTest();

        int v = TEX_V;
        if (this.isHoveredOrFocused() && this.enable) {
            v += TEX_V_HOVER;
        }
        blit(poseStack, this.x, this.y, TEX_U, v, this.width, this.height, 256, 256);

        if (!this.enable) {
            fill(poseStack, this.x, this.y, this.x + this.width, this.y + this.height, DISABLE_MASK);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, this.texture);
            blit(poseStack, this.x + LOCK_X, this.y, LOCK_U, LOCK_V, LOCK_W, LOCK_H, 256, 256);
        }

        // 图标
        float blitOffset = itemRenderer.blitOffset;
        itemRenderer.blitOffset = 10.0F;
        itemRenderer.renderGuiItem(this.task.getIcon(), this.x + 2, this.y + 2);
        itemRenderer.blitOffset = blitOffset;

        // 名称：与上游一致，不缩放（按钮高 19，字高 9，y+6 正好居中）
        minecraft.font.draw(poseStack, this.task.getName(), this.x + 23, this.y + 6, 0x333333);
    }

    @Nullable
    public List<Component> getTooltip() {
        return this.tooltip.isEmpty() ? null : this.tooltip;
    }
}
