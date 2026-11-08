package com.github.tartaricacid.touhoulittlemaid.client.gui.block;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.capability.IPower;
import com.github.tartaricacid.touhoulittlemaid.capability.PowerCapability;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.BeaconEffectButton;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityMaidBeacon;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityMaidBeacon.BeaconEffect;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 神社庭灯的界面，移植自上游 {@code client/gui/block/MaidBeaconGui}。
 *
 * <p><b>这不是原版的 {@code AbstractContainerMenu} 界面</b>，而是一个纯客户端
 * {@link Screen}：打开由服务端的 S2C 包触发（{@code MaidBeaconPacket.OPEN_GUI}），
 * 界面读的是<b>客户端侧方块实体</b>的同步数据，按钮点击则发 C2S 包。
 * 这样就不需要给庭灯造一个 Menu / Container，和上游的做法一致。</p>
 *
 * <p>界面分三块：</p>
 * <ul>
 *     <li>左侧：五个效果按钮（速度 / 抗火 / 力量 / 抗性 / 生命恢复）；</li>
 *     <li>右侧：庭灯存储量条 + 玩家灵力条，以及「存入一点 / 取出一点」两个按钮；</li>
 *     <li>底部：切换「溢出 P 点」是删除还是保留。</li>
 * </ul>
 *
 * <p><b>1.18.2 适配要点：</b>{@code Screen#renderables} 这个字段在 1.18.2 的原版里不存在
 * （1.19+ 才有），所以效果按钮由本类自己用 {@link #effectButtons} 记住，
 * 渲染提示框时遍历自己这份列表。</p>
 */
public class MaidBeaconGui extends Screen {
    private static final ResourceLocation BG =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/maid_beacon.png");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");

    private final TileEntityMaidBeacon beacon;
    private final List<BeaconEffectButton> effectButtons = new ArrayList<>();

    protected int imageWidth = 300;
    protected int imageHeight = 113;
    protected int leftPos;
    protected int topPos;

    /**
     * 界面里的「本地状态」：点击按钮先改这里并重建界面，再等服务端的方块更新到位。
     * 如果每次 {@code init()} 都从方块实体重读，点击后会因为一次网络往返而「闪回」。
     */
    private boolean overflowDelete;
    private int potionIndex;

    public MaidBeaconGui(TileEntityMaidBeacon beacon) {
        super(new TextComponent("Maid Beacon GUI"));
        this.beacon = beacon;
        this.overflowDelete = beacon.isOverflowDelete();
        this.potionIndex = beacon.getPotionIndex();
    }

    /**
     * 收到服务端的「打开界面」包后调用：确认坐标处确实有庭灯方块实体再开界面。
     *
     * <p>方块实体数据是随方块更新包同步过来的，所以直接读客户端实例即可。</p>
     */
    public static void openIfPresent(Minecraft mc, net.minecraft.core.BlockPos pos) {
        if (mc.level == null || mc.player == null || !mc.player.isAlive()) {
            return;
        }
        if (mc.level.getBlockEntity(pos) instanceof TileEntityMaidBeacon beacon) {
            mc.setScreen(new MaidBeaconGui(beacon));
        }
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.effectButtons.clear();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        this.addEffectButton(this.leftPos + 146, 26, this.topPos + 19);
        this.addStorageAndTakeButton();
        this.addRenderableWidget(new Button(this.leftPos + 118, this.topPos + 94, 154, 20,
                getOverflowDeleteButtonText(this.overflowDelete), b -> {
            this.overflowDelete = !this.overflowDelete;
            MaidBeaconClientPacketSender.sendOverflow(this.beacon.getBlockPos(), this.overflowDelete);
            this.init();
        }));
    }

    /**
     * 「存入一点 / 取出一点」：数量固定是 1.00 灵力（上游同值）。
     */
    private void addStorageAndTakeButton() {
        this.addRenderableWidget(new Button(this.leftPos + 118, this.topPos + 72, 76, 20,
                new TranslatableComponent("gui.touhou_little_maid.maid_beacon.add_one"), b ->
                MaidBeaconClientPacketSender.sendStoreOrTake(this.beacon.getBlockPos(), 1, true)));
        this.addRenderableWidget(new Button(this.leftPos + 196, this.topPos + 72, 76, 20,
                new TranslatableComponent("gui.touhou_little_maid.maid_beacon.min_one"), b ->
                MaidBeaconClientPacketSender.sendStoreOrTake(this.beacon.getBlockPos(), 1, false)));
    }

    private void addEffectButton(int start, int spacing, int y) {
        this.addEffectButton(BeaconEffect.SPEED, start, y);
        this.addEffectButton(BeaconEffect.FIRE_RESISTANCE, start + spacing, y);
        this.addEffectButton(BeaconEffect.STRENGTH, start + spacing * 2, y);
        this.addEffectButton(BeaconEffect.RESISTANCE, start + spacing * 3, y);
        this.addEffectButton(BeaconEffect.REGENERATION, start + spacing * 4, y);
    }

    private void addEffectButton(BeaconEffect effect, int x, int y) {
        BeaconEffectButton button = new BeaconEffectButton(effect, x, y, this.potionIndex, this.beacon,
                isStateTriggered -> {
                    // 同一时刻只能有一个效果：把其它按钮的按下状态交给服务端覆盖，
                    // 本地的 potionIndex 立刻改成新值（或 -1），然后重建界面。
                    this.potionIndex = isStateTriggered ? effect.ordinal() : -1;
                    this.init();
                });
        this.effectButtons.add(button);
        this.addRenderableWidget(button);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, BG);

        // 左面板 + 顶部标题条
        blit(poseStack, this.leftPos, this.topPos + 2, 0, 0, 142, 111);
        blit(poseStack, this.leftPos + 118, this.topPos + 1, 44, 111, 154, 15);

        // 两个条形的边框（存储量 / 玩家灵力）
        blit(poseStack, this.leftPos + 224, this.topPos + 44, 44, 126, 12, 12);
        blit(poseStack, this.leftPos + 224, this.topPos + 58, 44, 138, 12, 12);

        blit(poseStack, this.leftPos + 146, this.topPos + 46, 58, 128, 74, 9);
        blit(poseStack, this.leftPos + 146, this.topPos + 59, 58, 128, 74, 9);
        float percent = Mth.clamp(this.beacon.getStoragePower() / this.beacon.getMaxStorage(), 0.0F, 1.0F);
        blit(poseStack, this.leftPos + 146, this.topPos + 48, 58, 138, (int) (74 * percent), 5);

        this.renderPlayerPower(poseStack);
        drawString(poseStack, this.font, DECIMAL_FORMAT.format(this.beacon.getStoragePower()),
                this.leftPos + 240, this.topPos + 46, 0xFFFFFF);
        if (this.potionIndex == -1) {
            this.drawCenteredStringNoShadow(poseStack, this.font,
                    I18n.get("gui.touhou_little_maid.maid_beacon.cost_power", DECIMAL_FORMAT.format(0)),
                    this.leftPos + 195, this.topPos + 5, ChatFormatting.DARK_GRAY.getColor());
        } else {
            this.drawCenteredStringNoShadow(poseStack, this.font,
                    new TranslatableComponent("gui.touhou_little_maid.maid_beacon.cost_power",
                            DECIMAL_FORMAT.format(this.beacon.getEffectCost() * 900))
                            .withStyle(ChatFormatting.RED),
                    this.leftPos + 195, this.topPos + 5, 0xFFFFFF);
        }
        super.render(poseStack, mouseX, mouseY, partialTicks);
        // 效果按钮的提示框要画在按钮之上
        this.effectButtons.forEach(button -> button.renderToolTip(poseStack, this, mouseX, mouseY));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 按 E（物品栏键）关闭界面，与原版容器界面一致。
     *
     * <p>1.18.2 用的是 {@code KeyMapping#matches(int, int)}；上游写的
     * {@code isActiveAndMatches} 是 Forge 给 1.19+ 的补丁。</p>
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void renderPlayerPower(PoseStack poseStack) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            float power = IPower.getPower(player).get();
            float percent = power / PowerCapability.MAX_POWER;
            blit(poseStack, this.leftPos + 146, this.topPos + 61, 58, 143, (int) (74 * percent), 5);
            drawString(poseStack, this.font, DECIMAL_FORMAT.format(power),
                    this.leftPos + 240, this.topPos + 60, 0xFFFFFF);
        }
    }

    private MutableComponent getOverflowDeleteButtonText(boolean overflowDelete) {
        return overflowDelete
                ? new TranslatableComponent("gui.touhou_little_maid.maid_beacon.overflow_delete_true")
                : new TranslatableComponent("gui.touhou_little_maid.maid_beacon.overflow_delete_false");
    }

    /**
     * 居中绘制、不带阴影。原版没有「按 float 居中」的重载，所以自己算宽度。
     */
    private void drawCenteredStringNoShadow(PoseStack poseStack, Font font, String text, int x, int y, int color) {
        font.draw(poseStack, text, x - font.width(text) / 2f, y, color);
    }

    private void drawCenteredStringNoShadow(PoseStack poseStack, Font font, Component text, int x, int y, int color) {
        font.draw(poseStack, text, x - font.width(text) / 2f, y, color);
    }
}
