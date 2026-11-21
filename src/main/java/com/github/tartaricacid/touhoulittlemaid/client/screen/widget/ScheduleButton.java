package com.github.tartaricacid.touhoulittlemaid.client.screen.widget;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.screen.MaidClientPacketSender;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.schedule.Activity;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 女仆界面左下角的「日程表」按钮，移植自上游 1.18.2
 * {@code client/gui/widget/button/ScheduleButton}（1.20.1 只差 {@code GuiGraphics} 那一层包装）。
 *
 * <p>61x13，贴在三个开关（家 / 拾取 / 抱）上方那一格，位置 {@code leftPos+9, topPos+187}。
 * 贴图取 {@code maid_gui_button.png} 的 {@code (82, 43 + 14 * ordinal)} ——
 * 三种作息各有一张 61x13 的图，所以「显示当前作息」不需要另外画文字。</p>
 *
 * <p>点一下按 {@code DAY -> NIGHT -> ALL -> DAY} 轮转（上游用 {@code (ordinal + 1) % length}），
 * 并且在<b>本地先改</b>再发包。本地不改的话按钮要等服务端同步回来才变，
 * 中间那几帧看着像「点了没反应」。</p>
 *
 * <h2>提示文字的画法</h2>
 * <p>上游用 {@code String.format} 拼带 {@code \u00a7} 码的字面量：下划线 + 当前作息名、
 * 灰色时间，再到每个时段前面放一个「正在做 = 绿点 / 不在做 = 灰点」。
 * 这里改用 {@link ChatFormatting} + {@link MutableComponent}，渲染结果一致，
 * 但不再依赖字符串里的裸格式码。</p>
 */
public class ScheduleButton extends Button {
    private static final ResourceLocation BUTTON =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/maid_gui_button.png");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("00");

    private final EntityMaid maid;
    private MaidSchedule mode;

    public ScheduleButton(int x, int y, EntityMaid maid) {
        super(x, y, 61, 13, TextComponent.EMPTY, b -> {
        });
        this.maid = maid;
        this.mode = maid.getSchedule();
    }

    @Override
    public void onPress() {
        int index = this.mode.ordinal() + 1;
        int length = MaidSchedule.values().length;
        this.mode = MaidSchedule.values()[index % length];
        MaidClientPacketSender.sendSchedule(this.mode, this.maid.getId());
    }

    /**
     * 服务端同步值变了就跟着变（本次切换的回包，或别的玩家改了作息）。
     */
    public void syncFromMaid() {
        MaidSchedule actual = this.maid.getSchedule();
        if (actual != this.mode) {
            this.mode = actual;
        }
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, BUTTON);
        RenderSystem.enableDepthTest();
        blit(poseStack, this.x, this.y, 82, 43 + 14 * this.mode.ordinal(),
                this.width, this.height, 256, 256);
    }

    /**
     * 悬停提示：第一行「当前作息 + 当前时间」，随后按时段列出「做什么」，
     * 当前正在做的那一行是绿点 + 白色，其余灰点 + 灰色。
     *
     * <p>时刻换算是上游原样：原版 1 tick 的 0 点是早上 6:00，
     * 所以 {@code hour = time / 1000 + 6}、{@code minute = (time % 1000) * 60 / 1000}。</p>
     */
    public List<Component> getTooltips() {
        int time = (int) (this.maid.level.getDayTime() % 24000L);
        int hour = (time / 1000 + 6) % 24;
        int minute = (time % 1000) * 60 / 1000;
        Activity activity = this.maid.getScheduleDetail();

        List<Component> out = new ArrayList<>();
        out.add(new TextComponent(this.getScheduleTransText())
                .withStyle(ChatFormatting.UNDERLINE)
                .append(new TextComponent(" " + DECIMAL_FORMAT.format(hour) + ":"
                        + DECIMAL_FORMAT.format(minute)).withStyle(ChatFormatting.GRAY)));

        switch (this.mode) {
            case ALL -> out.add(timelineLine(activity == Activity.WORK, "00:00-24:00", Activity.WORK));
            case NIGHT -> {
                out.add(timelineLine(activity == Activity.WORK, "18:00-06:00", Activity.WORK));
                out.add(timelineLine(activity == Activity.REST, "06:00-14:00", Activity.REST));
                out.add(timelineLine(activity == Activity.IDLE, "14:00-18:00", Activity.IDLE));
            }
            default -> {
                out.add(timelineLine(activity == Activity.WORK, "06:00-18:00", Activity.WORK));
                out.add(timelineLine(activity == Activity.IDLE, "18:00-22:00", Activity.IDLE));
                out.add(timelineLine(activity == Activity.REST, "22:00-06:00", Activity.REST));
            }
        }
        out.add(new TranslatableComponent("tooltips.touhou_little_maid.schedule.desc"));
        return out;
    }

    /**
     * 一行「方块图标 + 时段 + 行为」。{@code active} 为真时绿点 + 白色，否则灰点 + 灰色。
     */
    private static Component timelineLine(boolean active, String range, Activity activity) {
        ChatFormatting lit = active ? ChatFormatting.WHITE : ChatFormatting.GRAY;
        return new TextComponent("\u2588 ")
                .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                .append(new TextComponent(range).withStyle(lit))
                .append(new TextComponent(" ").withStyle(ChatFormatting.GRAY))
                .append(new TextComponent(getActivityTransText(activity)).withStyle(lit));
    }

    public String getScheduleTransText() {
        return I18n.get("gui.touhou_little_maid.schedule." + this.mode.name().toLowerCase(Locale.US));
    }

    public static String getActivityTransText(Activity activity) {
        return I18n.get("gui.touhou_little_maid.activity." + activity.getName());
    }
}
