package com.github.tartaricacid.touhoulittlemaid.client.gui.item;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.WirelessIOSlotButton;
import com.github.tartaricacid.touhoulittlemaid.item.ItemWirelessIO;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import static com.github.tartaricacid.touhoulittlemaid.util.BytesBooleansConvert.booleans2Bytes;
import static com.github.tartaricacid.touhoulittlemaid.util.BytesBooleansConvert.bytes2Booleans;

/**
 * 隙间的「槽位配置」界面：38 个方格，点亮 = 该槽位<b>不参与</b>传输，
 * 移植自上游 {@code client/gui/item/WirelessIOConfigSlotGui}。
 *
 * <h2>38 个格子的排法（照抄上游，别自己重排）</h2>
 * <pre>
 *   y=8     6 格   ← 只看不用：这是「女仆背包 + 双手」拼接后的头 6 格
 *   y=30    6 格
 *   y=53    6 格
 *   y=71    6 格
 *   y=94    6 格
 *   y=112   6 格   ← 到这里是 36 个背包槽
 *   y=8/26  右侧 2 格  ← 女仆的<b>双手</b>
 * </pre>
 *
 * <p>也就是说这个界面上<b>没有标号</b>，靠位置对应槽位：
 * 女仆背包从左上开始按行填，最后两格单独放在右侧 ——
 * 因为「手」在女仆的物品栏里不属于背包，客户端也没法知道她现在背的是哪个背包。
 * {@code WirelessIOBauble} 那边会把这两格映射到物品栏的最后两位，
 * 这正是「换背包之后双手的开关还能记住」的原因。</p>
 *
 * <h2>为什么用 Screen 而不是菜单</h2>
 * <p>它没有物品格，只是在改一个 {@code byte[]}。做成菜单反而要多一条同步链路，
 * 而上游同样用纯客户端 {@link Screen}：点「确认」把整个数组发出去，
 * 服务端写完 NBT 再重开主界面。</p>
 */
public class WirelessIOConfigSlotGui extends Screen {
    private static final ResourceLocation SLOT =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/wireless_io_slot_config.png");
    private static final int SLOT_NUM = 38;

    private final boolean[] configData;

    protected int imageWidth = 155;
    protected int imageHeight = 160;
    protected int leftPos;
    protected int topPos;

    public WirelessIOConfigSlotGui(ItemStack wirelessIO) {
        super(new TextComponent("Wireless IO Config Slot GUI"));
        this.configData = bytes2Booleans(ItemWirelessIO.getSlotConfig(wirelessIO), SLOT_NUM);
    }

    @Override
    protected void init() {
        this.clearWidgets();

        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        int index = 0;
        // 前两行 6+6：背包第 0..11 格
        for (int col = 0; col < 6; col++) {
            index = this.addSlotButton(index, 8 + 18 * col, 8);
        }
        for (int col = 0; col < 6; col++) {
            index = this.addSlotButton(index, 8 + 18 * col, 30);
        }
        // 中间四行 2×(6+6)：背包第 12..35 格
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 6; col++) {
                index = this.addSlotButton(index, 8 + 18 * col, 53 + 18 * row);
            }
        }
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 6; col++) {
                index = this.addSlotButton(index, 8 + 18 * col, 94 + 18 * row);
            }
        }
        // 右侧两格：配置表的第 36、37 项，也就是女仆的两只手
        for (int row = 0; row < 2; row++) {
            index = this.addSlotButton(index, 131, 8 + 18 * row);
        }

        Button confirm = new Button(this.leftPos, this.topPos + 140, 60, 20,
                new TranslatableComponent("gui.done"),
                b -> WirelessIOClientPacketSender.sendSlotConfig(booleans2Bytes(this.configData)));
        Button cancel = new Button(this.leftPos + 62, this.topPos + 140, 60, 20,
                new TranslatableComponent("gui.cancel"),
                // 空数组 = 「只重开界面，不改数据」。断线或服务端拒绝时也不会留下半套配置
                b -> WirelessIOClientPacketSender.sendSlotConfig(null));
        this.addRenderableWidget(confirm);
        this.addRenderableWidget(cancel);
    }

    /**
     * 加一个方格并返回下一个可用的序号。抽出来是因为上面五段循环重复了同一件事。
     */
    private int addSlotButton(int index, int x, int y) {
        WirelessIOSlotButton button = new WirelessIOSlotButton(index, this.leftPos + x, this.topPos + y,
                16, 16, this.configData);
        button.initTextureValues(158, 0, 16, 16, SLOT);
        this.addRenderableWidget(button);
        return index + 1;
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBg(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks);
    }

    private void renderBg(PoseStack poseStack) {
        this.renderBackground(poseStack);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SLOT);
        this.blit(poseStack, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    /**
     * 这是一个纯客户端 Screen，谁都能随时按 E 关掉。
     *
     * <p>重写 {@code isPauseScreen} 返回 {@code false}：单人游戏里打开菜单不会暂停世界，
     * 否则女仆会「卡住」，玩家看到的就是「一开这个界面搬东西就停了」。
     * 上游没有重写（默认 {@code true}），但它是从另一个界面里点进来的，
     * 而且上游的界面也不暂停 —— 这里统一成不暂停更符合直觉。</p>
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
