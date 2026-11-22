package com.github.tartaricacid.touhoulittlemaid.client.screen.ai;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.FlatColorButton;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.GetSiteListPacket;
import com.github.tartaricacid.touhoulittlemaid.network.SaveSitePacket;
import com.github.tartaricacid.touhoulittlemaid.network.SyncSiteListPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import org.apache.commons.lang3.StringUtils;
import java.util.ArrayList;
import java.util.List;
/**
 * Site manager screen, modelled on 1.20.1's AIChatSettingsLLMSiteScreen / TTSSiteScreen.
 *
 * <p>Left: three category tabs (LLM / TTS / STT). Right: the sites of the current
 * category, one row each (icon, name, edit, delete). Bottom: new site and back.</p>
 *
 * <p>Deviation: 1.20.1 has one screen per type and real site icons. This port uses a
 * single screen with tabs and the first letter as the icon.</p>
 */
public class AISiteListScreen extends Screen {
    private static final String[] TAB_KEYS = {
            "ai.touhou_little_maid.chat.settings.tab.llm",
            "ai.touhou_little_maid.chat.settings.tab.tts",
            "ai.touhou_little_maid.chat.settings.tab.stt",
    };
    private static final String[] TAB_TYPES = {"chat", "tts", "stt"};
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_PADDING = 4;
    private static final int TAB_WIDTH = 96;
    private final EntityMaid maid;
    private int tab = 0;
    private int scroll = 0;
    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    public AISiteListScreen(EntityMaid maid) {
        super(new TranslatableComponent("ai.touhou_little_maid.chat.settings.title"));
        this.maid = maid;
    }
    @Override
    protected void init() {
        this.listX = 24 + TAB_WIDTH + 8;
        this.listY = 24;
        this.listWidth = this.width - this.listX - 24;
        this.listHeight = this.height - 24 - 56;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            final int index = i;
            this.addRenderableWidget(new FlatColorButton(24, 24 + i * 24, TAB_WIDTH, 20,
                    new TranslatableComponent(TAB_KEYS[i]), b -> {
                this.tab = index;
                this.scroll = 0;
            }));
        }
        int bottomY = this.height - 30;
        this.addRenderableWidget(new FlatColorButton(this.listX, bottomY, this.listWidth - 100, 20,
                new TranslatableComponent("ai.touhou_little_maid.chat.settings.new_site"),
                b -> this.openEditor(null)));
        this.addRenderableWidget(new Button(this.width - 24 - 96, bottomY, 96, 20,
                new TranslatableComponent("gui.back"), b -> this.onClose()));
        GetSiteListPacket.send();
    }
    /** Called when a new site list arrives. */
    public void refresh() {
    }
    private List<SyncSiteListPacket.Entry> currentSites() {
        String type = TAB_TYPES[this.tab];
        List<SyncSiteListPacket.Entry> out = new ArrayList<>();
        for (SyncSiteListPacket.Entry entry : SyncSiteListPacket.CLIENT_SITES) {
            if (type.equals(entry.type())) {
                out.add(entry);
            }
        }
        return out;
    }
    private void openEditor(SyncSiteListPacket.Entry entry) {
        this.minecraft.setScreen(new AISiteEditScreen(this.maid, this, TAB_TYPES[this.tab], entry));
    }
    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        fill(poseStack, this.listX, this.listY, this.listX + this.listWidth,
                this.listY + this.listHeight, 0x50_000000);
        List<SyncSiteListPacket.Entry> sites = this.currentSites();
        int maxRows = Math.max(1, this.listHeight / ROW_HEIGHT);
        for (int i = 0; i < maxRows; i++) {
            int index = i + this.scroll;
            if (index >= sites.size()) {
                break;
            }
            SyncSiteListPacket.Entry entry = sites.get(index);
            int rowY = this.listY + i * ROW_HEIGHT;
            boolean hover = mouseX >= this.listX && mouseX <= this.listX + this.listWidth
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hover) {
                fill(poseStack, this.listX + 1, rowY, this.listX + this.listWidth - 1,
                        rowY + ROW_HEIGHT - 1, 0x30FFFFFF);
            }
            String icon = StringUtils.defaultIfBlank(entry.name(), "?").substring(0, 1).toUpperCase();
            drawString(poseStack, this.font, new TextComponent(icon),
                    this.listX + ROW_PADDING, rowY + 7, 0xFFFFFF);
            drawString(poseStack, this.font, entry.name(),
                    this.listX + ROW_PADDING + 14, rowY + 7,
                    entry.enabled() ? 0xFFFFFF : 0x808080);
            drawString(poseStack, this.font, new TextComponent("\u270E"),
                    this.listX + this.listWidth - 42, rowY + 7, 0x7FE0FF);
            drawString(poseStack, this.font, new TextComponent("\u2716"),
                    this.listX + this.listWidth - 20, rowY + 7, 0xFF8080);
        }
        if (sites.isEmpty()) {
            drawCenteredString(poseStack, this.font,
                    new TranslatableComponent("ai.touhou_little_maid.chat.settings.empty"),
                    this.listX + this.listWidth / 2, this.listY + this.listHeight / 2, 0x999999);
        }
        super.render(poseStack, mouseX, mouseY, partialTicks);
    }
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= this.listX && mouseX <= this.listX + this.listWidth
                && mouseY >= this.listY && mouseY <= this.listY + this.listHeight) {
            int row = (int) ((mouseY - this.listY) / ROW_HEIGHT);
            int index = row + this.scroll;
            List<SyncSiteListPacket.Entry> sites = this.currentSites();
            if (index >= 0 && index < sites.size()) {
                SyncSiteListPacket.Entry entry = sites.get(index);
                if (mouseX >= this.listX + this.listWidth - 28) {
                    SaveSitePacket.sendDelete(entry.name());
                    return true;
                }
                this.openEditor(entry);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int rows = this.currentSites().size();
        int maxRows = Math.max(1, this.listHeight / ROW_HEIGHT);
        int max = Math.max(0, rows - maxRows);
        this.scroll = (int) Math.max(0, Math.min(max, this.scroll - delta));
        return true;
    }
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
