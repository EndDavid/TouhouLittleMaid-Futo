package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.github.tartaricacid.touhoulittlemaid.client.resource.MaidModelRegistry;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.CustomModelPack;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 女仆换肤界面，移植自上游 {@code client/gui/entity/model/MaidModelGui}。
 *
 * <p>布局全部在 {@link AbstractModelGui}，这里只做三件女仆相关的事：左侧大立绘、
 * 网格缩略图、点格子后切模型。</p>
 *
 * <h2>为什么这里不需要任何构建调度</h2>
 *
 * <p>上游在加载阶段就把全部模型几何烘焙进内存（见 {@code GeckoLibCache} 的类注释：
 * 1.18.2 默认包 194 个模型全部在资源重载时构建完），所以画 55 个缩略图只是纯粹的渲染开销。</p>
 *
 * <p>本移植版现在与上游一致：{@code GeckoModelLoader#reload()} 同步构建每个模型的几何与动画。
 * 因此这里直接取缓存即可 —— 只有构建失败的坏条目会返回 {@code null}，那时跳过这一格，
 * 否则渲染器会每帧为它刷一条警告。</p>
 */
public class MaidModelGui extends AbstractModelGui<EntityMaid, MaidModelInfo> {
    private static int PAGE_INDEX = 0;
    private static int PACK_INDEX = 0;
    private static int ROW_INDEX = 0;

    @Nullable
    private EntityMaid previewEntity;

    public MaidModelGui(EntityMaid maid, List<CustomModelPack<MaidModelInfo>> packs) {
        super(maid, packs);
    }

    /** 换肤按钮的入口：从注册表取包列表直接开界面。 */
    public static void open(EntityMaid maid) {
        Minecraft.getInstance().setScreen(new MaidModelGui(maid, MaidModelRegistry.allPacks()));
    }

    @Override
    protected void init() {
        super.init();
    }

    /** 该模型几何是否可用（只读探测，绝不触发构建）。 */
    private static boolean isReady(@Nullable ResourceLocation id) {
        return id != null && GeckoLibCache.getInstance().hasGeoModel(id);
    }

    @Override
    protected void drawLeftEntity(int middleX, int middleY, float mouseX, float mouseY) {
        ResourceLocation current = ResourceLocation.tryParse(this.entity.getModelId());
        // 构建失败的模型先不画，否则渲染器会拿 null 模型并刷警告
        if (!isReady(current)) {
            return;
        }
        EntityMaid preview = this.previewEntity();
        if (preview == null) {
            return;
        }
        preview.setModelId(this.entity.getModelId());
        MaidModelInfo info = MaidModelRegistry.get(this.entity.getModelId());
        float renderItemScale = info == null ? 1.0F : info.getRenderItemScale();
        InventoryScreen.renderEntityInInventory((middleX - 256 / 2) / 2, middleY + 90,
                (int) (45 * renderItemScale),
                (middleX - 256 / 2f) / 2 - mouseX, middleY + 80 - 40 - mouseY, preview);
    }

    @Override
    protected void drawRightEntity(PoseStack poseStack, int posX, int posY, MaidModelInfo modelItem) {
        ResourceLocation id = modelItem.getModelId();
        if (!isReady(id)) {
            return;
        }
        EntityMaid preview = this.previewEntity();
        if (preview == null) {
            return;
        }
        preview.setModelId(id.toString());
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
     * 所以必须发包。顺手清空 {@code handItemsForAnimation} —— 那是「手持物有没有换」的判据，
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
            EntityMaid maid = created instanceof EntityMaid m
                    ? m : new EntityMaid(InitEntities.MAID, level);
            maid.setSilent(true);
            maid.setNoGravity(true);
            maid.setCustomNameVisible(false);
            this.previewEntity = maid;
        }
        return this.previewEntity;
    }
}