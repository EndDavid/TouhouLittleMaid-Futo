package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.FlatColorButton;
import com.github.tartaricacid.touhoulittlemaid.network.FoxScrollPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * 狐狸卷轴的坐标列表界面，移植自上游 {@code client/gui/item/FoxScrollScreen}。
 *
 * <p>左侧一列是维度按钮，右侧每页 5 条记录：名字、坐标、与玩家的直线距离，
 * 以及一枚「追踪」按钮。点追踪会把这条坐标写进主手卷轴（C2S 包
 * {@link FoxScrollPacket#SET_TRACK}），之后世界里一直画着指向它的浮标。</p>
 *
 * <h2>为什么距离只在同维度时才算</h2>
 * <p>跨维度的方块坐标之间没有「距离」这个概念 —— 两个维度的坐标原点毫无关系，
 * 算出来的数字会被玩家当成真实距离。上游因此显示 {@code ???m}。</p>
 *
 * <h2>数据从哪来</h2>
 * <p>界面自己不查任何东西：服务端在 {@code ItemFoxScroll#use} 里把记录打包发过来，
 * 客户端只是展示。这样「玩家能看到哪些女仆」这件事只有一个判断点（服务端），
 * 客户端也无法凭空构造别人的记录。</p>
 */
@Environment(EnvType.CLIENT)
public class FoxScrollScreen extends Screen {
    /**
     * 每页条数。一条占 42 像素高（40 高的底板 + 2 像素间隔），
     * 208 高的面板正好放 5 条，剩下的空间给翻页按钮。
     */
    private static final int PER_PAGE_COUNT = 5;

    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 208;

    /**
     * 记录底板色（含 alpha）。
     */
    private static final int ROW_BACKGROUND = 0xEF58626B;

    private final Map<String, List<FoxScrollPacket.FoxScrollData>> data;
    private int leftPos;
    private int topPos;
    private String selectDim;
    private int page = 0;

    public FoxScrollScreen(Map<String, List<FoxScrollPacket.FoxScrollData>> data) {
        super(new TextComponent("Fox Scroll"));
        this.data = data;
        if (!this.data.isEmpty()) {
            this.selectDim = this.data.keySet().iterator().next();
        }
    }

    /**
     * 客户端收包后调用：统一从客户端入口开界面，避免在 common 侧引用 {@code Screen}。
     */
    public static void open(Map<String, List<FoxScrollPacket.FoxScrollData>> data) {
        Minecraft.getInstance().setScreen(new FoxScrollScreen(data));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.leftPos = (this.width - PANEL_WIDTH) / 2;
        this.topPos = (this.height - PANEL_HEIGHT) / 2;
        this.addDimensionButtons();
        this.addPointButtons();
    }

    private void addPointButtons() {
        if (this.selectDim == null || !this.data.containsKey(this.selectDim)) {
            return;
        }
        List<FoxScrollPacket.FoxScrollData> scrollData = this.data.get(this.selectDim);
        int lastPage = (scrollData.size() - 1) / PER_PAGE_COUNT;
        // 翻页按钮只在这一维度确实超过一页时出现
        if (scrollData.size() > PER_PAGE_COUNT) {
            this.addRenderableWidget(new FlatColorButton(this.leftPos + PANEL_WIDTH - 20, this.topPos,
                    20, 20, new TextComponent("\u2191"), b -> {
                if (this.page > 0) {
                    this.page--;
                    this.init();
                }
            }));
            this.addRenderableWidget(new FlatColorButton(this.leftPos + PANEL_WIDTH - 20,
                    this.topPos + PANEL_HEIGHT - 20, 20, 20, new TextComponent("\u2193"), b -> {
                if (this.page < lastPage) {
                    this.page++;
                    this.init();
                }
            }));
        }
        int offsetIn = this.topPos;
        for (int i = this.page * PER_PAGE_COUNT; i < this.page * PER_PAGE_COUNT + PER_PAGE_COUNT; i++) {
            if (i < scrollData.size()) {
                FoxScrollPacket.FoxScrollData info = scrollData.get(i);
                this.addRenderableWidget(new FlatColorButton(this.leftPos + PANEL_WIDTH - 90,
                        offsetIn + 11, 60, 20,
                        new TranslatableComponent("gui.touhou_little_maid.fox_scroll.track"), b -> {
                    FriendlyByteBuf buf = PacketByteBufs.create();
                    buf.writeByte(FoxScrollPacket.SET_TRACK);
                    buf.writeUtf(this.selectDim, FoxScrollPacket.MAX_DIMENSION_LENGTH);
                    buf.writeBlockPos(info.getPos());
                    ClientPlayNetworking.send(FoxScrollPacket.CHANNEL, buf);
                }));
                offsetIn = offsetIn + 42;
            }
        }
    }

    private void addDimensionButtons() {
        int offset = this.topPos;
        for (String dim : this.data.keySet()) {
            FlatColorButton dimButton = new FlatColorButton(this.leftPos, offset, 150, 19,
                    new TextComponent(dim), b -> {
                this.selectDim = dim;
                this.page = 0;
                this.init();
            });
            if (dim.equals(this.selectDim)) {
                dimButton.setSelect(true);
            }
            this.addRenderableWidget(dimButton);
            offset = offset + 21;
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        if (this.data.isEmpty()) {
            // 空表和「有维度但一条都没有」都走这里。后者在服务端不会发生
            // （空维度不会进 map），但客户端防御一下不会有坏处
            drawCenteredString(poseStack, this.font,
                    new TranslatableComponent("gui.touhou_little_maid.fox_scroll.empty"),
                    this.width / 2, this.height / 2 - 5, 0xFF0000);
            return;
        }
        this.renderMain(poseStack);
        // super.render 最后调：它负责画所有按钮，压在底板之上
        super.render(poseStack, mouseX, mouseY, partialTicks);
    }

    private void renderMain(PoseStack poseStack) {
        if (this.selectDim == null || !this.data.containsKey(this.selectDim)) {
            return;
        }
        List<FoxScrollPacket.FoxScrollData> scrollData = this.data.get(this.selectDim);
        String playerDimension = this.getPlayerDimension();
        boolean inSameDim = this.selectDim.equals(playerDimension);
        BlockPos playerPos = this.getPlayerPos();
        int offsetIn = this.topPos;
        for (int i = this.page * PER_PAGE_COUNT; i < this.page * PER_PAGE_COUNT + PER_PAGE_COUNT; i++) {
            if (i >= scrollData.size()) {
                break;
            }
            FoxScrollPacket.FoxScrollData info = scrollData.get(i);
            BlockPos pos = info.getPos();
            Component distanceText;
            if (inSameDim) {
                // 玩家在动，所以每帧都要重算；这里用平方根而不是直接显示平方距离，
                // 是因为玩家看到的是「多少米」而不是「多少平方米」
                int distance = (int) Math.sqrt(playerPos.distSqr(pos));
                distanceText = new TranslatableComponent(
                        "gui.touhou_little_maid.fox_scroll.distance.same_dimension", distance);
            } else {
                distanceText = new TranslatableComponent(
                        "gui.touhou_little_maid.fox_scroll.distance.different_dimension");
            }
            Component posText = new TranslatableComponent(
                    "gui.touhou_little_maid.fox_scroll.position", pos.toShortString());
            fill(poseStack, this.leftPos + 152, offsetIn,
                    this.leftPos + PANEL_WIDTH - 22, offsetIn + 40, ROW_BACKGROUND);
            this.font.draw(poseStack, info.getName(), this.leftPos + 160, offsetIn + 4,
                    ChatFormatting.GOLD.getColor());
            this.font.draw(poseStack, posText, this.leftPos + 160, offsetIn + 16,
                    ChatFormatting.GRAY.getColor());
            this.font.draw(poseStack, distanceText, this.leftPos + 160, offsetIn + 28,
                    ChatFormatting.GRAY.getColor());
            offsetIn = offsetIn + 42;
        }
        if (scrollData.size() > PER_PAGE_COUNT) {
            String pageText = String.format("%d/%d", this.page + 1,
                    (scrollData.size() - 1) / PER_PAGE_COUNT + 1);
            drawCenteredString(poseStack, this.font, pageText,
                    this.leftPos + PANEL_WIDTH - 8, this.topPos + PANEL_HEIGHT / 2 - 5,
                    ChatFormatting.GRAY.getColor());
        }
    }

    /**
     * 与上游一致：看坐标时游戏不暂停（单人游戏里也要让女仆继续走）。
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private BlockPos getPlayerPos() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            return minecraft.player.blockPosition();
        }
        return BlockPos.ZERO;
    }

    @Nullable
    private String getPlayerDimension() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            return minecraft.player.level.dimension().location().toString();
        }
        return null;
    }
}

