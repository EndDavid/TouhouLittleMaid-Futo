package com.github.tartaricacid.touhoulittlemaid.client.screen.ai;

import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.FlatColorButton;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.Site;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.SaveSitePacket;
import com.github.tartaricacid.touhoulittlemaid.network.SyncSiteListPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Edit one site: name, url, api key and the comma separated model list.
 *
 * <p>Mirrors 1.20.1's LLMSiteEditorScreen / TTSSiteEditorScreen in what it edits; the
 * layout is a simple vertical form because the 1.20.1 screens carry per api type form
 * descriptors that this port does not have.</p>
 */
public class AISiteEditScreen extends Screen {
    private static final int ROW = 24;
    private static final int LABEL_WIDTH = 72;

    private final EntityMaid maid;
    private final Screen parent;
    private final String type;
    private final boolean creating;
    private final String originalName;
    private final String apiType;

    private EditBox nameField;
    private EditBox urlField;
    private EditBox keyField;
    private EditBox modelsField;

    public AISiteEditScreen(EntityMaid maid, Screen parent, String type,
                            SyncSiteListPacket.Entry entry) {
        super(new TranslatableComponent("ai.touhou_little_maid.chat.settings.edit_site"));
        this.maid = maid;
        this.parent = parent;
        this.type = type;
        this.creating = entry == null;
        this.originalName = entry == null ? "" : entry.name();
        this.apiType = entry == null ? "" : entry.apiType();
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 160;
        int fieldX = left + LABEL_WIDTH;
        int fieldWidth = this.width - fieldX - 24;
        int y = 40;

        this.nameField = field(fieldX, y, fieldWidth, originalName, 48);
        y += ROW;
        this.urlField = field(fieldX, y, fieldWidth, url(), 256);
        y += ROW;
        this.keyField = field(fieldX, y, fieldWidth, apiKey(), 256);
        y += ROW;
        this.modelsField = field(fieldX, y, fieldWidth, models(), 1024);
        y += ROW + 8;

        int buttonWidth = 96;
        this.addRenderableWidget(new FlatColorButton(fieldX, y, buttonWidth, 20,
                new TranslatableComponent("selectWorld.edit.save"), b -> this.save()));
        this.addRenderableWidget(new FlatColorButton(fieldX + buttonWidth + 4, y, buttonWidth, 20,
                new TranslatableComponent("gui.cancel"), b -> this.onClose()));
    }

    private EditBox field(int x, int y, int width, String value, int maxLength) {
        EditBox box = new EditBox(this.font, x, y, width, 18, TextComponent.EMPTY);
        box.setMaxLength(maxLength);
        box.setValue(StringUtils.defaultString(value));
        this.addRenderableWidget(box);
        return box;
    }

    private SyncSiteListPacket.Entry source() {
        for (SyncSiteListPacket.Entry entry : SyncSiteListPacket.CLIENT_SITES) {
            if (entry.name().equals(this.originalName)) {
                return entry;
            }
        }
        return null;
    }

    private String url() {
        SyncSiteListPacket.Entry entry = source();
        return entry == null ? "" : entry.url();
    }

    private String apiKey() {
        SyncSiteListPacket.Entry entry = source();
        return entry == null ? "" : entry.apiKey();
    }

    private String models() {
        SyncSiteListPacket.Entry entry = source();
        return entry == null ? "" : String.join(", ", entry.models());
    }

    /**
     * 保存后这个站点是否启用。
     *
     * <p>已有站点沿用列表里的开关值；<b>但只要这次填了像样的 api_key 就强制启用</b> ——
     * 否则「在编辑界面填好 key 却仍然不算配置」会让人完全摸不着头脑
     * （这正是之前请求打到 127.0.0.1 的那一类困惑）。</p>
     */
    private boolean enabled(String apiKey) {
        SyncSiteListPacket.Entry entry = source();
        boolean current = entry != null && entry.enabled();
        return current || Site.isUsableApiKey(apiKey);
    }

    private void save() {
        String name = StringUtils.trimToEmpty(this.nameField.getValue());
        if (StringUtils.isBlank(name)) {
            // A blank name cannot be addressed from the list, so refuse to save.
            return;
        }
        List<String> models = new ArrayList<>();
        for (String part : StringUtils.defaultString(this.modelsField.getValue()).split(",")) {
            String model = StringUtils.trimToEmpty(part);
            if (StringUtils.isNotBlank(model)) {
                models.add(model);
            }
        }
        String apiKey = StringUtils.trimToEmpty(this.keyField.getValue());
        SaveSitePacket.sendSave(name, this.type, this.apiType,
                StringUtils.trimToEmpty(this.urlField.getValue()), apiKey, models,
                this.enabled(apiKey));
        this.onClose();
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 16, 0xFFFFFF);

        int left = this.width / 2 - 160;
        int y = 40;
        drawString(poseStack, this.font, new TextComponent("Name"), left, y + 5, 0xFFFFFF);
        y += ROW;
        drawString(poseStack, this.font, new TextComponent("URL"), left, y + 5, 0xFFFFFF);
        y += ROW;
        drawString(poseStack, this.font, new TextComponent("API Key"), left, y + 5, 0xFFFFFF);
        y += ROW;
        drawString(poseStack, this.font, new TextComponent("Models"), left, y + 5, 0xFFFFFF);
        y += ROW + 4;
        drawString(poseStack, this.font,
                new TranslatableComponent("ai.touhou_little_maid.chat.settings.models.tip"),
                left, y, 0x999999);

        if (this.creating) {
            drawString(poseStack, this.font,
                    new TranslatableComponent("ai.touhou_little_maid.chat.settings.edit_site.new"),
                    left, y + 12, 0x999999);
        }

        super.render(poseStack, mouseX, mouseY, partialTicks);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

