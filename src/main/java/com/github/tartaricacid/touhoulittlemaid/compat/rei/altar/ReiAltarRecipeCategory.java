package com.github.tartaricacid.touhoulittlemaid.compat.rei.altar;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.compat.rei.MaidREIClientPlugin;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.client.gui.GuiComponent.blit;

/**
 * REI 里「祭坛合成」这一分类的画布，移植自上游 {@code compat/rei/altar/ReiAltarRecipeCategory}。
 *
 * <p><b>画布是一个 3x2 的戒指形</b>：祭坛本体不参与合成，是玩家把材料按这个形状
 * 摆在祭坛四周（两个在下、两个在上、左右各一个），所以六个格子不排成一行，
 * 而是照祭坛九宫格里「除去中心」的六个位置摆。中间画一个点数图标表示这个配方要多少 P。</p>
 *
 * <p>尺寸与坐标全部照抄上游：{@code 160x125}，格子间距 20，结果格在右上角。
 * 这样两边的观感一致，也便于对照上游截图排查。</p>
 */
public class ReiAltarRecipeCategory implements DisplayCategory<ReiAltarRecipeDisplay> {
    private static final MutableComponent TITLE = new TranslatableComponent("jei.touhou_little_maid.altar_craft.title");
    private static final ResourceLocation ALTAR_ICON =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/altar_icon.png");
    private static final ResourceLocation POWER_ICON =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/entity/power_point.png");

    private final Renderer icon;

    public ReiAltarRecipeCategory() {
        this.icon = new Renderer() {
            @Override
            public void render(PoseStack matrices, Rectangle bounds, int mouseX, int mouseY, float delta) {
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.setShaderTexture(0, ALTAR_ICON);
                blit(matrices, bounds.x, bounds.y, 0, 0, 16, 16, 16, 16);
            }

            @Override
            public int getZ() {
                return 100;
            }

            @Override
            public void setZ(int z) {
                // 分类图标是固定位置的静态贴图，不需要调整层级
            }
        };
    }

    @Override
    public CategoryIdentifier<? extends ReiAltarRecipeDisplay> getCategoryIdentifier() {
        return MaidREIClientPlugin.ALTAR;
    }

    @Override
    public Component getTitle() {
        return TITLE;
    }

    @Override
    public Renderer getIcon() {
        return icon;
    }

    @Override
    public int getDisplayHeight() {
        return 125;
    }

    @Override
    public int getDisplayWidth(ReiAltarRecipeDisplay display) {
        return 160;
    }

    @Override
    public List<Widget> setupDisplay(ReiAltarRecipeDisplay display, Rectangle bounds) {
        int darkGray = 0x555555;
        Font font = Minecraft.getInstance().font;
        String result = I18n.get("jei.touhou_little_maid.altar_craft.result", I18n.get(display.getLangKey()));
        int startX = bounds.x;
        int startY = bounds.y + 5;

        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        // 六个材料格：下二、上二、左右各一 —— 祭坛九宫格里除中心外的位置
        widgets.add(Widgets.createSlot(new Point(startX + 40, startY + 35))
                .entries(getInput(display.getInputEntries(), 0)).markInput());
        widgets.add(Widgets.createSlot(new Point(startX + 40, startY + 55))
                .entries(getInput(display.getInputEntries(), 1)).markInput());
        widgets.add(Widgets.createSlot(new Point(startX + 60, startY + 15))
                .entries(getInput(display.getInputEntries(), 2)).markInput());
        widgets.add(Widgets.createSlot(new Point(startX + 80, startY + 15))
                .entries(getInput(display.getInputEntries(), 3)).markInput());
        widgets.add(Widgets.createSlot(new Point(startX + 100, startY + 35))
                .entries(getInput(display.getInputEntries(), 4)).markInput());
        widgets.add(Widgets.createSlot(new Point(startX + 100, startY + 55))
                .entries(getInput(display.getInputEntries(), 5)).markInput());
        widgets.add(Widgets.createSlot(new Point(startX + 140 - 5, startY + 5))
                .entries(display.getOutputEntries().get(0)).markOutput());

        // 灵力消耗图标：power_point.png 是 64x64 的图集，第 0 帧就在左上角 32 格处
        widgets.add(Widgets.createTexturedWidget(POWER_ICON, startX + 72, startY + 38, 32, 0, 16, 16, 64, 64));
        widgets.add(Widgets.withTranslate(Widgets.createDrawableWidget((helper, matrices, mouseX, mouseY, v) -> {
            font.draw(matrices, result, 0, 0, darkGray);
        }), startX + (int) ((bounds.getWidth() - font.width(result)) / 2.0f), startY + 85, 0));

        widgets.add(Widgets.withTranslate(Widgets.createDrawableWidget((helper, matrices, mouseX, mouseY, v) -> {
            font.draw(matrices, String.format("%.2f", display.getPowerCost()), 0, 0, darkGray);
        }), startX + 65, startY + 55, 0));

        return widgets;
    }

    /** 配方原料不足 6 项时补齐空格子，否则画布上会缺格。 */
    private EntryIngredient getInput(List<EntryIngredient> inputs, int index) {
        if (index < inputs.size()) {
            return inputs.get(index);
        }
        return EntryIngredient.empty();
    }
}
