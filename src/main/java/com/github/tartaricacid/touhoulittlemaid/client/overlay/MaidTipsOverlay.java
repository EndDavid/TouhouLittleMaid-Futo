package com.github.tartaricacid.touhoulittlemaid.client.overlay;

import com.github.tartaricacid.touhoulittlemaid.config.subconfig.RenderConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 「手持某物指向女仆」时在准星旁给出的操作提示。
 *
 * <p>完全对应上游 1.20.1 {@code client/overlay/MaidTipsOverlay}：</p>
 * <ul>
 *     <li>只在<b>第一人称</b>、非旁观者、准星真的命中一只活着的女仆时显示；</li>
 *     <li>手持物在提示表里、且女仆<b>归属自己</b>时才显示 ——
 *         这条限制很关键：对着别人的女仆提示「右击取出经验」是错的；</li>
 *         <li>另一个例外是「特殊条件」类提示（剪刀拆背包、NTR 物品），
 *         它们走的是 {@link #specialTips}，判定条件各自实现。</li>
 * </ul>
 *
 * <p>排版与上游一致：图标那一行在「屏幕中心上方 5 像素、再按行数往上抬」的位置，
 * 依次画手持物、提示图标（{@code textures/gui/maid_tips_icon.png}），
 * 正文按 120 像素宽度折行，每行 10 像素。</p>
 *
 * <p>与上游的差异：</p>
 * <ul>
 *     <li>上游继承 Forge 的 {@code IGuiOverlay}，这里改成 Fabric 的
 *         {@code HudRenderCallback}（{@code onHudRender(MatrixStack, float)}）；</li>
 *     <li>上游的 {@code gui.setupOverlayRenderState(true, false)} 用于恢复混合状态，
 *         这里显式 {@code enableBlend} + 默认混合函数代替；</li>
 *     <li>上游用 {@code GuiGraphics}（1.20 才有），1.18.2 只有 {@code PoseStack} +
 *         {@code GuiComponent#drawString} / {@code blit}，所以自绘时手动带 depth 关闭。</li>
 * </ul>
 */
public final class MaidTipsOverlay {
    private static final net.minecraft.resources.ResourceLocation ICON =
            new net.minecraft.resources.ResourceLocation("touhou_little_maid", "textures/gui/maid_tips_icon.png");

    /**
     * 正文最大宽度（像素）。上游同值。
     */
    private static final int MAX_TEXT_WIDTH = 120;
    private static final int LINE_HEIGHT = 10;

    private MaidTipsOverlay() {
    }

    public static void init() {
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT
                .register((poseStack, tickDelta) -> render(poseStack));
    }

    private static void render(PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        if (minecraft.gameMode == null || minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return;
        }
        if (!(minecraft.hitResult instanceof EntityHitResult result)) {
            return;
        }
        if (!(result.getEntity() instanceof EntityMaid maid) || !maid.isAlive()) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        // 和女仆骑同一个载具时准星会来回命中两者，提示会闪，直接不画（上游同款处理）
        if (player.getVehicle() != null && player.getVehicle().equals(maid.getVehicle())) {
            return;
        }

        ItemStack itemStack = player.getMainHandItem();
        Component tip = pickTip(itemStack, maid, player);
        if (tip == null) {
            return;
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        List<FormattedCharSequence> lines = minecraft.font.split(tip, MAX_TEXT_WIDTH);
        int x = screenWidth / 2 + 32;
        int y = (screenHeight / 2 - 5) - lines.size() * LINE_HEIGHT;

        // 强制深度测试关闭 + 混合，避免 HUD 被实体 / 方块挡住
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        minecraft.getItemRenderer().renderAndDecorateItem(itemStack, x, y);
        RenderSystem.setShaderTexture(0, ICON);
        GuiComponent.blit(poseStack, x + 10, y - 2, 0, 0, 16, 16, 16, 16);

        int textY = y + 18;
        for (FormattedCharSequence line : lines) {
            GuiComponent.drawString(poseStack, minecraft.font, line, x, textY, 0xFFFFFF);
            textY += LINE_HEIGHT;
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * 选出要显示的提示，{@code null} 表示不显示。
     *
     * <p>顺序与上游一致：先看「归属自己 + 普通提示表」，
     * 命中且对应配置开启就直接用；否则再试「特殊条件提示」。</p>
     */
    @Nullable
    private static Component pickTip(ItemStack stack, EntityMaid maid, LocalPlayer player) {
        Item item = stack.getItem();
        if (maid.isOwnedByPlayer(player)) {
            // 玻璃瓶：只有女仆身上真的有经验（≥12 点）时才提示，否则「右击取出经验」
            // 是一句谎话 —— 上游没做这个判断，但那样 0 经验也会弹提示，很容易误导。
            if (item == Items.GLASS_BOTTLE) {
                if (!enabled(RenderConfig.ENABLE_GLASS_BOTTLE_TIP) || maid.getExperience() < 12) {
                    return specialTip(stack, maid, player);
                }
                return new TranslatableComponent("overlay.touhou_little_maid.glass_bottle.tips");
            }
            if (item == Items.COMPASS && enabled(RenderConfig.ENABLE_COMPASS_TIP)) {
                return new TranslatableComponent("overlay.touhou_little_maid.compass.tips");
            }
            if ((item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE)
                    && enabled(RenderConfig.ENABLE_GOLDEN_APPLE_TIP)) {
                return new TranslatableComponent("overlay.touhou_little_maid.golden_apple.tips");
            }
            if (item == Items.POTION && enabled(RenderConfig.ENABLE_POTION_TIP)) {
                return new TranslatableComponent("overlay.touhou_little_maid.potion.tips");
            }
            if (item == Items.MILK_BUCKET && enabled(RenderConfig.ENABLE_MILK_BUCKET_TIP)) {
                return new TranslatableComponent("overlay.touhou_little_maid.milk_bucket.tips");
            }
            if (item == Items.NAME_TAG && enabled(RenderConfig.ENABLE_NAME_TAG_TIP)) {
                return new TranslatableComponent("overlay.touhou_little_maid.name_tag.tips");
            }
            if (item == Items.LEAD && enabled(RenderConfig.ENABLE_LEAD_TIP)) {
                return new TranslatableComponent("overlay.touhou_little_maid.lead.tips");
            }
            if (item == Items.SADDLE && enabled(RenderConfig.ENABLE_SADDLE_TIP)) {
                return new TranslatableComponent("overlay.touhou_little_maid.saddle.tips");
            }
        }
        return specialTip(stack, maid, player);
    }

    /**
     * 「特殊条件」提示：不看在不在普通表里，而是看条件。
     */
    @Nullable
    private static Component specialTip(ItemStack stack, EntityMaid maid, LocalPlayer player) {
        // 别人的女仆 + 手里是 NTR 物品
        if (!maid.isOwnedByPlayer(player) && maid.getNtrItem().test(stack)) {
            return new TranslatableComponent("overlay.touhou_little_maid.ntr_item.tips");
        }
        // 自己的女仆 + 剪刀 + 当前有背包
        if (maid.isOwnedByPlayer(player) && maid.hasBackpack()
                && stack.getItem() instanceof net.minecraft.world.item.ShearsItem) {
            return new TranslatableComponent("overlay.touhou_little_maid.remove_backpack.tips");
        }
        return null;
    }

    /**
     * 读布尔配置，并在配置未加载时回退 {@code true}。
     *
     * <p>提示是纯客户端渲染，可能在配置加载完成之前就被调用；
     * {@code ForgeConfigSpec.BooleanValue#get()} 在那种情况下会抛异常。</p>
     */
    private static boolean enabled(net.minecraftforge.common.ForgeConfigSpec.BooleanValue value) {
        try {
            return value == null || value.get();
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 兼容旧调用点的空文本（避免 {@code null} 被当成「有提示」画出来）。
     */
    public static MutableComponent empty() {
        return new TextComponent("").withStyle(ChatFormatting.RESET);
    }

    /**
     * 预留：上游用 {@code Map<Item, MutableComponent>} 建表，这里改成 if 链以支持
     * 「玻璃瓶额外要求有经验」这种带条件的条目。这个接口只为说明映射关系，不参与渲染。
     */
    static Map<Item, String> keyTable() {
        return Map.of(
                Items.COMPASS, "overlay.touhou_little_maid.compass.tips",
                Items.GLASS_BOTTLE, "overlay.touhou_little_maid.glass_bottle.tips");
    }
}

