package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.github.tartaricacid.touhoulittlemaid.client.resource.ChairModelRegistry;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.ChairModelInfo;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityChairRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.network.ChairModelPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 坐垫换肤界面，移植自上游 {@code client/gui/entity/model/ChairModelGui}。
 *
 * <p>它就是 {@link AbstractModelGui} 在坐垫场景下的一份具体实现。布局、分页、滚动、
 * 页签全部在基类里，这里只做三件坐垫相关的事：左侧大立绘、网格缩略图、
 * 点一格后发 {@link ChairModelPacket} 让服务端改写实体数据。</p>
 *
 * <h2>为什么要单独一个类</h2>
 * <p>上游 {@code AbstractModelGui<T extends LivingEntity, E extends IModelInfo>} 就是这么泛型化的：
 * 女仆与坐垫共用同一套网格，差别只在「一个格子画的是什么模型」。本移植版的基类
 * 原先写死成 {@code MaidModelInfo}，所以坐垫只能靠创造模式物品栏里的各个变体切换；
 * 泛型化之后这里才写得出来。</p>
 *
 * <h2>与上游的差异</h2>
 * <ol>
 *     <li>上游 Shift + 点击格子会开 {@code ChairModelDetailsGui}（模型详情页）。
 *         本移植版没有那个界面，{@link AbstractModelGui} 也没有对应的钩子，
 *         行为与 {@link MaidModelGui} 一致：只有普通点击。</li>
 *     <li>上游有「图标缓存」分支（{@code MiscConfig.MODEL_ICON_CACHE}），本移植版没有图标缓存，
 *         永远实时画实体。</li>
 * </ol>
 */
public class ChairModelGui extends AbstractModelGui<EntityChair, ChairModelInfo> {
    // 三个下标做成 static：上游同样是「关掉界面再打开保持上次的位置」
    private static int PAGE_INDEX = 0;
    private static int PACK_INDEX = 0;
    private static int ROW_INDEX = 0;

    /**
     * 网格缩略图专用的假坐垫。
     *
     * <p>{@code EntityChair} 的构造器会建一个客户端 {@code GeckoChairEntity}，而 GeckoLib
     * 的动画状态是按实体实例缓存的 —— 每格新建既慢，又会让动画永远停在第一帧。
     * {@code Entity#level} 是 {@code final}，所以只在换世界时重建。</p>
     */
    @Nullable
    private EntityChair previewEntity;

    public ChairModelGui(EntityChair chair) {
        super(chair, ChairModelRegistry.allPacks());
    }

    /** 入口：从注册表取包列表直接开界面。 */
    public static void open(EntityChair chair) {
        Minecraft.getInstance().setScreen(new ChairModelGui(chair));
    }

    /** 该模型几何是否可用（只读探测，绝不触发构建）。 */
    private static boolean isReady(@Nullable ResourceLocation id) {
        return id != null && GeckoLibCache.getInstance().hasGeoModel(id);
    }

    /**
     * 界面打开期间临时关掉「碰撞箱显示器」的红框。
     *
     * <p>打开这个界面必须<b>蹲着</b>右键坐垫，而「蹲下 + 手持 {@code chair_show}」正是红框模式的
     * 触发条件 —— 不管的话，左边大立绘和网格里 55 个缩略图会全变成红色线框。
     * 与 {@link com.github.tartaricacid.touhoulittlemaid.client.renderer.item.ChairItemRenderer}
     * 关掉它是同一个理由（物品图标里也会多一个红框）。</p>
     *
     * <p>用 {@code finally} 恢复：界面渲染中途抛异常也不能把这个全局开关永久留在关闭态。</p>
     */
    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        boolean oldHitBox = EntityChairRenderer.renderHitBox;
        EntityChairRenderer.renderHitBox = false;
        try {
            super.render(poseStack, mouseX, mouseY, partialTicks);
        } finally {
            EntityChairRenderer.renderHitBox = oldHitBox;
        }
    }

    @Override
    protected void drawLeftEntity(int middleX, int middleY, float mouseX, float mouseY) {
        String current = this.entity.getModelId();
        if (!isReady(ResourceLocation.tryParse(current))) {
            return;
        }
        float renderItemScale = ChairModelRegistry.getModelRenderItemScale(current);
        // 与上游一致：左侧用真实实体（它的 model_id 会被服务端同步改写），
        // 缩放用「物品图标」那个系数 —— 坐垫模型普遍比女仆小得多。
        InventoryScreen.renderEntityInInventory((middleX - 256 / 2) / 2, middleY + 80,
                (int) (45 * renderItemScale), -25, -20, this.entity);
    }

    @Override
    protected void drawRightEntity(PoseStack poseStack, int posX, int posY, ChairModelInfo modelItem) {
        ResourceLocation id = modelItem.getModelId();
        if (!isReady(id)) {
            return;
        }
        EntityChair preview = this.previewEntity();
        if (preview == null) {
            return;
        }
        preview.setModelId(id.toString());
        InventoryScreen.renderEntityInInventory(posX, posY,
                (int) (12 * modelItem.getRenderItemScale()), -25, -20, preview);
    }

    /** 与上游一致：坐垫没有额外提示行（坐高 / 可骑写在物品 tooltip 里）。 */
    @Override
    protected void addModelCustomTips(ChairModelInfo modelItem, List<Component> tooltips) {
    }

    /**
     * 点了一格：只发包，不在本地改。
     *
     * <p>{@code ModelId} / {@code MountedHeight} / {@code TameableCanRide} 都是
     * {@code SynchedEntityData}，本地先改会被服务端下一次同步覆盖，表现是「点了闪一下又弹回去」。
     * 而且「能不能换模型」由 {@code ChairConfig.CHAIR_CHANGE_MODEL} 决定，必须服务端判定。</p>
     *
     * <p>界面<b>不关闭</b>：上游 {@code notifyModelChange} 也只发包，让玩家能连点着比较几个模型。</p>
     */
    @Override
    protected void onModelClicked(ChairModelInfo modelItem) {
        ChairModelPacket.send(this.entity.getId(),
                modelItem.getModelId(),
                modelItem.getMountedYOffset(),
                modelItem.isTameableCanRide(),
                modelItem.isNoGravity());
    }

    @Override
    protected String getCurrentModelId() {
        return this.entity.getModelId();
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

    @Nullable
    private EntityChair previewEntity() {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        if (this.previewEntity == null || this.previewEntity.level != level) {
            EntityChair chair = InitEntities.CHAIR.create(level);
            if (chair == null) {
                chair = new EntityChair(level);
            }
            chair.setSilent(true);
            chair.setNoGravity(true);
            chair.setCustomNameVisible(false);
            this.previewEntity = chair;
        }
        return this.previewEntity;
    }
}
