package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.github.tartaricacid.touhoulittlemaid.client.resource.MaidModelRegistry;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.CustomModelPack;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 女仆换肤界面，移植自上游 {@code client/gui/entity/model/MaidModelGui}。
 *
 * <p>只有两个真正需要「女仆」的地方：左侧大立绘调用 {@code renderEntityInInventory}，
 * 以及点格子后发包改 {@code ModelId}。其余全部由 {@link AbstractModelGui} 负责。</p>
 *
 * <p><b>页签 / 页码是 static 的</b>（与上游一致）：关掉界面再打开会回到上次浏览的包和页，
 * 而不是每次都从第一页开始。</p>
 */
public class MaidModelGui extends AbstractModelGui<EntityMaid> {
    private static int PAGE_INDEX = 0;
    private static int PACK_INDEX = 0;
    private static int ROW_INDEX = 0;

    @Nullable
    private EntityMaid previewEntity;

    public MaidModelGui(EntityMaid maid, List<CustomModelPack> packs) {
        super(maid, packs);
    }

    /**
     * 便捷入口：从注册表取包列表直接开界面（换肤按钮走这里）。
     */
    public static void open(EntityMaid maid) {
        List<CustomModelPack> packs = MaidModelRegistry.allPacks();
        Minecraft.getInstance().setScreen(new MaidModelGui(maid, packs));
    }

    @Override
    protected void drawLeftEntity(int middleX, int middleY, float mouseX, float mouseY) {
        EntityMaid preview = this.previewEntity();
        if (preview == null) {
            return;
        }
        preview.setModelId(this.entity.getModelId());
        float renderItemScale = 1.0F;
        MaidModelInfo info = MaidModelRegistry.get(this.entity.getModelId());
        if (info != null) {
            renderItemScale = info.getRenderItemScale();
        }
        // 与上游一致：x 取左半区中点，y 为 midY+90，缩放 45*renderItemScale
        InventoryScreen.renderEntityInInventory((middleX - 256 / 2) / 2, middleY + 90,
                (int) (45 * renderItemScale),
                (middleX - 256 / 2f) / 2 - mouseX, middleY + 80 - 40 - mouseY, preview);
    }

    @Override
    protected void drawRightEntity(PoseStack poseStack, int posX, int posY, MaidModelInfo modelItem) {
        EntityMaid preview = this.previewEntity();
        if (preview == null) {
            return;
        }
        preview.setModelId(modelItem.getModelId().toString());
        InventoryScreen.renderEntityInInventory(posX, posY,
                (int) (12 * modelItem.getRenderItemScale()), -25, -20, preview);
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
     * 点了一格：本地先改（立绘立刻反馈），再发包。
     *
     * <p>{@code ModelId} 是 {@code SynchedEntityData}，只改本地会被服务端下一次同步覆盖，
     * 所以必须发包；本地先改只是为了不等一个往返。</p>
     *
     * <p>顺手清空 {@code handItemsForAnimation}：它是「手持物有没有换」的判据，
     * 不清的话新模型的手部骨骼会沿用上一个模型的姿态。</p>
     */
    @Override
    protected void onModelClicked(MaidModelInfo modelItem) {
        String modelId = modelItem.getModelId().toString();
        this.entity.setModelId(modelId);
        this.entity.getHandItemsForAnimation()[0] = ItemStack.EMPTY;
        this.entity.getHandItemsForAnimation()[1] = ItemStack.EMPTY;
        MaidClientPacketSender.sendModel(modelId, this.entity.getId());
    }

    @Override
    protected String getCurrentModelId() {
        return this.entity.getModelId();
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

    @Override
    protected int getPageIndex() {
        return PAGE_INDEX;
    }

    @Override
    protected void setPageIndex(int pageIndex) {
        PAGE_INDEX = pageIndex;
    }

    /**
     * 懒建缩略图实体，并校验它属于当前客户端世界。
     *
     * <p>{@code EntityMaid} 的构造器会建 Brain、属性表和两个 {@code ItemStackHandler}，
     * 而 {@code GeckoMaidEntity} 的动画缓存按实体实例做 key —— 每帧新建既慢，
     * 又会让动画永远停在第一帧。{@code Entity#level} 是 {@code final}，
     * 所以只在换世界时重建。</p>
     */
    @Nullable
    private EntityMaid previewEntity() {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            return null;
        }
        if (this.previewEntity == null || this.previewEntity.level != level) {
            Entity created = InitEntities.MAID.create(level);
            EntityMaid maid = created instanceof EntityMaid m ? m : new EntityMaid(InitEntities.MAID, level);
            maid.setSilent(true);
            maid.setNoGravity(true);
            maid.setCustomNameVisible(false);
            this.previewEntity = maid;
        }
        return this.previewEntity;
    }
}

