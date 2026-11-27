package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.AttackTaskConfigContainer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import org.anti_ad.mc.ipn.api.IPNButton;
import org.anti_ad.mc.ipn.api.IPNGuiHint;
import org.anti_ad.mc.ipn.api.IPNPlayerSideOnly;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;

/**
 * 攻击类工作模式的任务设置页，移植自上游 {@code AttackTaskConfigGui}。
 *
 * <p>布局（相对 GUI 原点）：</p>
 * <pre>
 * 输入框   (87, 36)  117x16
 * 添加按钮 (206, 35) 44x18
 * 上一页   (208, 56) 5x9   UV (0,176)
 * 下一页   (243, 56) 5x9   UV (5,176)
 * 名单 7 行 (86, 67 + 13*i)  164x13
 * </pre>
 *
 * <p><b>名单只在关闭界面时提交一次：</b>编辑过程中改的是菜单里的副本，
 * 每改一次就发一个包会让服务端反复重建女仆的行为树 —— 编辑十几条就是十几次重建，
 * 每次都要停掉所有正在跑的 Brain 行为。改完关闭时整体提交，
 * 语义与上游 {@code SetAttackListMessage} 一致，成本低得多。</p>
 *
 * <p>IPN 的五个 {@code @IPNGuiHint} 与 {@code @IPNPlayerSideOnly} 与上游
 * {@code AttackTaskConfigGui} 逐字相同（说明见 {@code MaidConfigScreen} 的类注释）。</p>
 */
@IPNPlayerSideOnly
@IPNGuiHint(button = IPNButton.SORT, horizontalOffset = -36, bottom = -12)
@IPNGuiHint(button = IPNButton.SORT_COLUMNS, horizontalOffset = -24, bottom = -24)
@IPNGuiHint(button = IPNButton.SORT_ROWS, horizontalOffset = -12, bottom = -36)
@IPNGuiHint(button = IPNButton.SHOW_EDITOR, horizontalOffset = -5)
@IPNGuiHint(button = IPNButton.SETTINGS, horizontalOffset = -5)
public class AttackTaskConfigScreen extends MaidTaskConfigScreen<AttackTaskConfigContainer> {
    private static final ResourceLocation BG =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/attack_task_config.png");

    /**
     * 每页 7 行（上游硬编码）。
     */
    private static final int ROWS_PER_PAGE = 7;

    private EditBox inputField;
    private int page = 0;
    /**
     * 当前页按「敌对 → 中立 → 友好」排序后的 id 列表。
     */
    private List<ResourceLocation> sortedKeys;

    public AttackTaskConfigScreen(AttackTaskConfigContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.resortKeys();
    }

    /**
     * 重新排序并把页码夹回合法范围。
     *
     * <p>删掉最后一条时总页数会减一，页码若不夹取就会停在空页上 ——
     * 表现是「删完一条以后整页空空如也，还以为全删了」。</p>
     */
    private void resortKeys() {
        this.sortedKeys = this.menu.getAttackList().sortedKeys();
        int maxPage = Math.max(0, (this.sortedKeys.size() - 1) / ROWS_PER_PAGE);
        this.page = Mth.clamp(this.page, 0, maxPage);
    }

    /**
     * 供 {@code MonsterListButton} 读写名单（就是菜单里那份客户端副本）。
     */
    public com.github.tartaricacid.touhoulittlemaid.entity.data.AttackListData getAttackList() {
        return this.menu.getAttackList();
    }

    public void removeMonsterType(ResourceLocation id) {
        this.menu.getAttackList().attackGroups().remove(id);
        this.resortKeys();
        this.clearWidgets();
        this.init();
    }

    @Override
    protected void renderAddition(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, BG);
        this.blit(poseStack, this.leftPos + 80, this.topPos + 28, 0, 0, this.imageWidth, 137);

        if (this.inputField != null) {
            this.inputField.render(poseStack, mouseX, mouseY, partialTicks);
        }

        int totalPages = Math.max(1, (this.sortedKeys.size() - 1) / ROWS_PER_PAGE + 1);
        MutableComponent pageText = new TextComponent(String.format("%d/%d", this.page + 1, totalPages));
        this.drawCenteredString(poseStack, this.font, pageText, this.leftPos + 228, this.topPos + 57, 0xFFFFFF);
        this.drawCenteredString(poseStack, this.font,
                new TranslatableComponent("gui.touhou_little_maid.monster_type.title"),
                this.leftPos + 147, this.topPos + 57, 0xFFFFFF);
    }

    @Override
    protected void initAdditionWidgets() {
        int startLeft = this.leftPos + 87;
        int startTop = this.topPos + 36;

        this.inputField = new EditBox(this.font, startLeft, startTop, 117, 16,
                new TextComponent("Monster List"));
        this.inputField.setMaxLength(256);
        this.addWidget(this.inputField);

        this.addRenderableWidget(new Button(startLeft + 119, startTop - 1, 44, 18,
                new TranslatableComponent("gui.touhou_little_maid.monster_type.add"),
                b -> this.addMonsterType()));

        // 翻页箭头（UV 在 attack_task_config.png 的左下角）
        this.addRenderableWidget(new ImageButton(startLeft + 121, startTop + 20, 5, 9, 0, 176, 9, BG, b -> {
            this.page--;
            this.resortKeys();
            this.clearWidgets();
            this.init();
        }));
        this.addRenderableWidget(new ImageButton(startLeft + 156, startTop + 20, 5, 9, 5, 176, 9, BG, b -> {
            this.page++;
            this.resortKeys();
            this.clearWidgets();
            this.init();
        }));

        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            int index = this.page * ROWS_PER_PAGE + i;
            if (index >= this.sortedKeys.size()) {
                return;
            }
            ResourceLocation id = this.sortedKeys.get(index);
            Optional<EntityType<?>> type = EntityType.byString(id.toString());
            if (type.isEmpty()) {
                continue;
            }
            int yOffset = startTop + 31 + 13 * i;
            this.addRenderableWidget(new com.github.tartaricacid.touhoulittlemaid.client.screen.widget.MonsterListButton(
                    type.get().getDescription(), startLeft - 1, yOffset, id, this));
        }
    }

    /**
     * 添加一条：新条目默认「中立」。
     *
     * <p>必须校验实体类型真实存在 —— 手滑输错一个字母就加进去，之后它在名单里
     * 永远不生效（没有任何实体有这个 id），玩家会以为是「名单坏了」。</p>
     */
    private void addMonsterType() {
        String value = this.inputField.getValue().trim();
        if (value.isEmpty() || !ResourceLocation.isValidResourceLocation(value)) {
            return;
        }
        ResourceLocation id = new ResourceLocation(value);
        Optional<EntityType<?>> entityType = EntityType.byString(value);
        if (entityType.isEmpty()) {
            return;
        }
        this.menu.getAttackList().attackGroups()
                .put(id, com.github.tartaricacid.touhoulittlemaid.entity.misc.MonsterType.NEUTRAL);
        this.inputField.setValue("");
        this.resortKeys();
        this.clearWidgets();
        this.init();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String value = this.inputField == null ? "" : this.inputField.getValue();
        super.resize(minecraft, width, height);
        if (this.inputField != null) {
            this.inputField.setValue(value);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.inputField != null) {
            this.inputField.tick();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 输入框里按 ESC 时上游是直接关容器；这里交给默认流程（同效，且会走 onClose）
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (this.inputField != null
                && (this.inputField.keyPressed(keyCode, scanCode, modifiers) || this.inputField.canConsumeInput())) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        MaidClientPacketSender.sendAttackList(this.menu.getAttackList(), this.maid.getId());
        super.onClose();
    }
}
