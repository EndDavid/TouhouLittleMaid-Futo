package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.github.tartaricacid.touhoulittlemaid.client.resource.MaidModelRegistry;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityModelSwitcher;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 模型切换器的「选皮肤」界面，移植自上游 {@code client/gui/block/ModelSwitcherModelGui}。
 *
 * <p>它就是 {@link AbstractModelGui} 在本场景下的一份具体实现：左侧大立绘、
 * 右侧模型网格、点一格就把 {@link TileEntityModelSwitcher.ModeInfo#setModelId} 换掉并退回
 * {@link ModelSwitcherGui}。</p>
 *
 * <h2>与 {@link MaidModelGui} 的分工</h2>
 * <p>两者共用同一套网格布局（都在 {@code AbstractModelGui} 里），区别只在「点了之后做什么」：</p>
 * <ul>
 *     <li>{@code MaidModelGui}：改实体模型 + 发 {@code MaidModelPacket} 给服务端；</li>
 *     <li>本类：改的是<b>方块实体里那条 {@code ModeInfo}</b>，改完退回模式列表界面，
 *         真正的落盘要等那边点「保存」或关界面（见 {@code SaveSwitcherDataPacket}）。</li>
 * </ul>
 *
 * <h2>与上游的差异</h2>
 * <ol>
 *     <li>上游的 {@code openDetailsGui}（Shift+点击开详情）本移植版没有对应实现 ——
 *         {@code MaidModelDetailsGui} 尚未移植，{@link AbstractModelGui} 也就没有这个钩子。
 *         行为与现有的 {@code MaidModelGui} 一致：只有普通点击。</li>
 *     <li>上游有「图标缓存」分支（{@code MiscConfig.MODEL_ICON_CACHE}），本移植版没有图标缓存，
 *         与 {@code AbstractModelGui} 的类注释一致，永远走实时渲染。</li>
 * </ol>
 */
public class ModelSwitcherModelGui extends AbstractModelGui<EntityMaid, MaidModelInfo> {
    // 三个下标做成 static：上游同样是「关掉界面再打开保持上次的位置」
    private static int PAGE_INDEX = 0;
    private static int PACK_INDEX = 0;
    private static int ROW_INDEX = 0;

    private final TileEntityModelSwitcher.ModeInfo infoIn;
    private final ModelSwitcherGui modelSwitcherGui;

    public ModelSwitcherModelGui(EntityMaid maid, TileEntityModelSwitcher.ModeInfo infoIn,
                                 ModelSwitcherGui modelSwitcherGui) {
        super(maid, MaidModelRegistry.allPacks());
        this.infoIn = infoIn;
        this.modelSwitcherGui = modelSwitcherGui;
    }

    /** 该模型几何是否可用（只读探测，绝不触发构建）。 */
    private static boolean isReady(@Nullable ResourceLocation id) {
        return id != null && GeckoLibCache.getInstance().hasGeoModel(id);
    }

    @Override
    protected void drawLeftEntity(int middleX, int middleY, float mouseX, float mouseY) {
        ResourceLocation current = this.infoIn.getModelId();
        if (!isReady(current)) {
            return;
        }
        this.entity.setModelId(current.toString());
        MaidModelInfo info = MaidModelRegistry.get(current);
        float renderScale = info == null ? 1.0F : info.getRenderItemScale();
        InventoryScreen.renderEntityInInventory((middleX - 256 / 2) / 2, middleY + 90,
                (int) (45 * renderScale),
                (middleX - 256 / 2f) / 2 - mouseX, middleY + 80 - 40 - mouseY, this.entity);
    }

    @Override
    protected void drawRightEntity(PoseStack poseStack, int posX, int posY, MaidModelInfo modelItem) {
        ResourceLocation id = modelItem.getModelId();
        if (!isReady(id)) {
            return;
        }
        this.entity.setModelId(id.toString());
        InventoryScreen.renderEntityInInventory(posX, posY,
                (int) (12 * modelItem.getRenderItemScale()), -25, -20, this.entity);
    }

    @Override
    protected void addModelCustomTips(MaidModelInfo modelItem, List<Component> tooltips) {
        String useSoundPackId = modelItem.getUseSoundPackId();
        if (useSoundPackId != null && !useSoundPackId.isBlank()) {
            tooltips.add(new TranslatableComponent(
                    "gui.touhou_little_maid.skin.tooltips.maid_use_sound_pack_id", useSoundPackId)
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /**
     * 点了一格：只改<b>模式条目</b>，然后退回模式列表界面。
     *
     * <p>不改女仆实体本身 —— 这只女仆要等红石脉冲时才由
     * {@code BlockModelSwitcher#setMaidData} 应用当前模式。</p>
     */
    @Override
    protected void onModelClicked(MaidModelInfo modelItem) {
        this.infoIn.setModelId(modelItem.getModelId());
        Minecraft.getInstance().setScreen(this.modelSwitcherGui);
    }

    @Override
    protected String getCurrentModelId() {
        return this.infoIn.getModelId() == null ? "" : this.infoIn.getModelId().toString();
    }

    @Override
    protected int getPageIndex() {
        return PAGE_INDEX;
    }

    @Override
    protected void setPageIndex(int pageIndex) {
        PAGE_INDEX = pageIndex;
    }

    @Override
    protected int getPackIndex() {
        return PACK_INDEX;
    }

    @Override
    protected void setPackIndex(int packIndex) {
        PACK_INDEX = packIndex;
    }

    @Override
    protected int getRowIndex() {
        return ROW_INDEX;
    }

    @Override
    protected void setRowIndex(int rowIndex) {
        ROW_INDEX = rowIndex;
    }
}
