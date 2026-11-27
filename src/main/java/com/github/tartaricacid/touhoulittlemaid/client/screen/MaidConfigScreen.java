package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.screen.widget.MaidConfigButton;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidSubConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.PickType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.TabIndex;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.MaidConfigContainer;
import com.github.tartaricacid.touhoulittlemaid.network.MaidConfigPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.anti_ad.mc.ipn.api.IPNButton;
import org.anti_ad.mc.ipn.api.IPNGuiHint;
import org.anti_ad.mc.ipn.api.IPNPlayerSideOnly;

/**
 * 女仆配置界面，移植自上游 {@code client/gui/entity/maid/config/MaidConfigContainerGui}。
 *
 * <p>8 行，每行一个 {@code MaidConfigButton}（164x13），纵向步距 13，
 * 起点 {@code leftPos+86, topPos+52}。底图 {@code maid_gui_config.png}
 * 画在 {@code leftPos+80, topPos+28}。</p>
 *
 * <p><b>数值不读实体、只读本地副本：</b>这些字段都是 {@code SynchedEntityData}，
 * 点一下按钮到服务端回写之间有几十毫秒延迟。若每帧都从实体读，会出现
 * 「点一下加 0.1，然后弹回原值，再变成新值」的抖动。所以这里在界面里维护
 * 一份 {@code local} 快照作为显示来源，点击时只改本地 + 发包；服务端那边
 * 是幂等写入，最终两边一致。</p>
 *
 * <h2>IPN 联动</h2>
 * <p>类上的五个 {@code @IPNGuiHint} 与上游 {@code MaidConfigContainerGui} 逐字相同：
 * 让 Inventory Profiles Next 在本界面右下角摆出「排序 / 按列排 / 按行排 / 编辑器 / 设置」五个按钮，
 * 底板由 {@code compat/ipn/SortButtonScreen} 画。</p>
 *
 * <p>{@code @IPNPlayerSideOnly} 是关键：本界面自己没有可排序的容器槽（女仆的 8 项配置是按钮，
 * 不是物品格），能排的只有底部那份<b>玩家背包</b>。少了这个注解，IPN 会以为整个菜单都该被排序。</p>
 *
 * <p>这些注解是 {@code RUNTIME} + {@code @Inherited}，由 IPN 自己用反射读。
 * 没装 IPN 时<b>没有任何东西会去读它们</b>，所以不构成硬依赖；编译期只需要 IPN 的 jar
 * （{@code modCompileOnly}）。</p>
 */
@IPNPlayerSideOnly
@IPNGuiHint(button = IPNButton.SORT, horizontalOffset = -36, bottom = -12)
@IPNGuiHint(button = IPNButton.SORT_COLUMNS, horizontalOffset = -24, bottom = -24)
@IPNGuiHint(button = IPNButton.SORT_ROWS, horizontalOffset = -12, bottom = -36)
@IPNGuiHint(button = IPNButton.SHOW_EDITOR, horizontalOffset = -5)
@IPNGuiHint(button = IPNButton.SETTINGS, horizontalOffset = -5)
public class MaidConfigScreen extends AbstractMaidScreen<MaidConfigContainer> {
    private static final ResourceLocation ICON =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/maid_gui_config.png");

    private static final int BUTTON_LEFT = 86;
    private static final int BUTTON_TOP = 52;
    private static final int ROW_STEP = 13;

    private boolean showBackpack;
    private boolean showBackItem;
    private boolean showChatBubble;
    private float soundFreq;
    private PickType pickType;
    private boolean openDoor;
    private boolean openFenceGate;
    private boolean activeClimbing;

    public MaidConfigScreen(MaidConfigContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        if (this.maid != null) {
            this.showBackpack = MaidSubConfig.isShowBackpack(this.maid);
            this.showBackItem = MaidSubConfig.isShowBackItem(this.maid);
            this.showChatBubble = MaidSubConfig.isShowChatBubble(this.maid);
            this.soundFreq = MaidSubConfig.getSoundFreq(this.maid);
            this.pickType = this.maid.getPickupType();
            this.openDoor = MaidSubConfig.canOpenDoor(this.maid);
            this.openFenceGate = MaidSubConfig.canOpenFenceGate(this.maid);
            this.activeClimbing = MaidSubConfig.isActiveClimbing(this.maid);
        }
    }

    @Override
    protected int getCurrentTab() {
        return TabIndex.MAID_CONFIG;
    }

    @Override
    protected void renderAddition(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, ICON);
        RenderSystem.enableDepthTest();
        this.blit(poseStack, this.leftPos + 80, this.topPos + 28, 0, 0, this.imageWidth, this.imageHeight);
        // 标题（上游画在 leftPos+140, topPos+41）
        this.font.draw(poseStack, new TranslatableComponent("gui.touhou_little_maid.button.maid_config"),
                this.leftPos + 140, this.topPos + 41, 0xFFFFFF);
    }

    @Override
    protected void initAdditionWidgets() {
        int y = this.topPos + BUTTON_TOP;

        // 1. 显示背包
        this.addRenderableWidget(new MaidConfigButton(this.leftPos + BUTTON_LEFT, y,
                key("show_backpack"), boolValue(this.showBackpack),
                b -> {
                    this.showBackpack = !this.showBackpack;
                    b.setValue(boolValue(this.showBackpack));
                    MaidClientPacketSender.sendConfig(MaidConfigPacket.SHOW_BACKPACK,
                            this.showBackpack, this.maid.getId());
                }));
        y += ROW_STEP;

        // 2. 显示背部物品
        this.addRenderableWidget(new MaidConfigButton(this.leftPos + BUTTON_LEFT, y,
                key("show_back_item"), boolValue(this.showBackItem),
                b -> {
                    this.showBackItem = !this.showBackItem;
                    b.setValue(boolValue(this.showBackItem));
                    MaidClientPacketSender.sendConfig(MaidConfigPacket.SHOW_BACK_ITEM,
                            this.showBackItem, this.maid.getId());
                }));
        y += ROW_STEP;

        // 3. 显示聊天气泡
        this.addRenderableWidget(new MaidConfigButton(this.leftPos + BUTTON_LEFT, y,
                key("show_chat_bubble"), boolValue(this.showChatBubble),
                b -> {
                    this.showChatBubble = !this.showChatBubble;
                    b.setValue(boolValue(this.showChatBubble));
                    MaidClientPacketSender.sendConfig(MaidConfigPacket.SHOW_CHAT_BUBBLE,
                            this.showChatBubble, this.maid.getId());
                }));
        y += ROW_STEP;

        // 4. 女仆声音频率（0% ~ 100%，左右箭头各 ±10%）
        this.addRenderableWidget(new MaidConfigButton(this.leftPos + BUTTON_LEFT, y,
                key("sound_frequency"), this.soundFreqText(),
                b -> this.changeSoundFreq(b, -0.1F),
                b -> this.changeSoundFreq(b, 0.1F)));
        y += ROW_STEP;

        // 5. 拾取类型（物品 / 经验 / 全部）
        this.addRenderableWidget(new MaidConfigButton(this.leftPos + BUTTON_LEFT, y,
                key("pick_type"), this.pickTypeText(),
                b -> this.changePickType(b, false),
                b -> this.changePickType(b, true)));
        y += ROW_STEP;

        // 6. 能否开门
        this.addRenderableWidget(new MaidConfigButton(this.leftPos + BUTTON_LEFT, y,
                key("open_door"), boolValue(this.openDoor),
                b -> {
                    this.openDoor = !this.openDoor;
                    b.setValue(boolValue(this.openDoor));
                    MaidClientPacketSender.sendConfig(MaidConfigPacket.OPEN_DOOR,
                            this.openDoor, this.maid.getId());
                }));
        y += ROW_STEP;

        // 7. 能否开栅栏门
        this.addRenderableWidget(new MaidConfigButton(this.leftPos + BUTTON_LEFT, y,
                key("open_fence_gate"), boolValue(this.openFenceGate),
                b -> {
                    this.openFenceGate = !this.openFenceGate;
                    b.setValue(boolValue(this.openFenceGate));
                    MaidClientPacketSender.sendConfig(MaidConfigPacket.OPEN_FENCE_GATE,
                            this.openFenceGate, this.maid.getId());
                }));
        y += ROW_STEP;

        // 8. 能否主动爬梯
        this.addRenderableWidget(new MaidConfigButton(this.leftPos + BUTTON_LEFT, y,
                key("active_climbing"), boolValue(this.activeClimbing),
                b -> {
                    this.activeClimbing = !this.activeClimbing;
                    b.setValue(boolValue(this.activeClimbing));
                    MaidClientPacketSender.sendConfig(MaidConfigPacket.ACTIVE_CLIMBING,
                            this.activeClimbing, this.maid.getId());
                }));
    }

    /**
     * 语音频率：本地先夹到 0~1 再显示，避免浮点累加到 1.0000001。
     */
    private void changeSoundFreq(MaidConfigButton button, float delta) {
        this.soundFreq = Mth.clamp(this.soundFreq + delta, 0.0F, 1.0F);
        button.setValue(this.soundFreqText());
        MaidClientPacketSender.sendSoundFreq(this.soundFreq, this.maid.getId());
    }

    private void changePickType(MaidConfigButton button, boolean next) {
        this.pickType = next ? PickType.getNextPickType(this.pickType)
                : PickType.getPreviousPickType(this.pickType);
        button.setValue(this.pickTypeText());
        MaidClientPacketSender.sendPickType(this.pickType, this.maid.getId());
    }

    private Component soundFreqText() {
        return new TextComponent(Math.round(this.soundFreq * 100) + "%").withStyle(ChatFormatting.YELLOW);
    }

    private Component pickTypeText() {
        return new TranslatableComponent(PickType.getTransKey(this.pickType))
                .withStyle(ChatFormatting.DARK_RED);
    }

    private static Component boolValue(boolean value) {
        return new TranslatableComponent("gui.touhou_little_maid.maid_config.value." + value);
    }

    private static Component key(String name) {
        return new TranslatableComponent("gui.touhou_little_maid.maid_config." + name);
    }
}

