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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 女仆换肤界面，移植自上游 {@code client/gui/entity/model/MaidModelGui}。
 *
 * <p>布局全部在 {@link AbstractModelGui}，这里只做三件女仆相关的事：左侧大立绘、
 * 网格缩略图、点格子后切模型。</p>
 *
 * <h2>为什么要自己排队构建几何</h2>
 *
 * <p>上游在资源加载阶段就把全部模型几何烘焙进内存（{@code CustomPackLoader} 那一步），
 * 所以它画网格时可以随便切 {@code ModelId}，没有构建开销。</p>
 *
 * <p>本移植版不是这样：旧版（1.10.0）模型有 189 个，全部预构建会把渲染线程卡死，
 * 于是 {@code GeckoLibCache#getGeoModel} 改成首次用到时构建。
 * 而一屏网格要画 55 个模型 —— 直接在 render 里逐个 {@code setModelId}，
 * 一帧内就会同步解析并构建 55 份基岩 JSON（芙兰朵露那份 3315 行、405 根骨骼），
 * 客户端当场卡死。选中某个模型时瞬间卡住也是同一个原因：那一刻正好触发了一份
 * 尚未构建的几何。</p>
 *
 * <p>所以这里加一个按帧预算的构建队列：</p>
 * <ul>
 *     <li>{@link #init()} 把当前页要显示的模型全部入队；</li>
 *     <li>{@link #render} 每帧只取队头若干个、且总耗时不超过 {@link #BUILD_BUDGET_NANOS}；</li>
 *     <li>几何还没就绪的格子这一帧不画（画了只会拿到 null 模型并刷警告），
 *         下一帧或几帧后自然出现；</li>
 *     <li>点格子时把目标模型插到队首，左侧大立绘等它构建好再切换。</li>
 * </ul>
 *
 * <p>已构建的模型记在 {@link #BUILD_DONE} 里，滚动 / 翻页 / 重开界面都不会重复构建。</p>
 */
public class MaidModelGui extends AbstractModelGui<EntityMaid> {
    private static int PAGE_INDEX = 0;
    private static int PACK_INDEX = 0;
    private static int ROW_INDEX = 0;

    /** 每帧花在构建几何上的时间上限（纳秒）。3ms 足够让界面保持流畅。 */
    private static final long BUILD_BUDGET_NANOS = 3_000_000L;
    /** 每帧最多构建几个，避免模型特别便宜时一口气吞掉整帧。 */
    private static final int MAX_BUILDS_PER_FRAME = 4;
    /** 已构建几何的模型（跨界面实例保留，翻页 / 重开不重复构建）。 */
    private static final Set<ResourceLocation> BUILD_DONE = new HashSet<>();
    /** 待构建队列（跨实例保留，重开界面不丢进度）。 */
    private static final Deque<ResourceLocation> BUILD_QUEUE = new ArrayDeque<>();

    @Nullable
    private EntityMaid previewEntity;

    public MaidModelGui(EntityMaid maid, List<CustomModelPack> packs) {
        super(maid, packs);
    }

    /** 换肤按钮的入口：从注册表取包列表直接开界面。 */
    public static void open(EntityMaid maid) {
        Minecraft.getInstance().setScreen(new MaidModelGui(maid, MaidModelRegistry.allPacks()));
    }

    @Override
    protected void init() {
        super.init();
        this.enqueueCurrentPage();
        // 当前模型要显示在左边，排到队首
        enqueueModel(ResourceLocation.tryParse(this.entity.getModelId()), true);
    }

    /** 把当前页要显示的模型入队。 */
    private void enqueueCurrentPage() {
        if (packCount() <= 0) {
            return;
        }
        CustomModelPack pack = MaidModelRegistry.allPacks().get(getPackIndex());
        if (pack == null) {
            return;
        }
        List<MaidModelInfo> list = pack.getModelList();
        int from = Math.max(0, getRowIndex() * 11);
        int to = Math.min(list.size(), from + 5 * 11);
        for (int i = from; i < to; i++) {
            enqueueModel(list.get(i).getModelId(), false);
        }
    }

    /**
     * 入队一个模型。
     *
     * @param front true 时插到队首（点击 / 当前模型优先）
     */
    private static void enqueueModel(@Nullable ResourceLocation id, boolean front) {
        if (id == null || BUILD_DONE.contains(id) || BUILD_QUEUE.contains(id)) {
            return;
        }
        if (front) {
            BUILD_QUEUE.addFirst(id);
        } else {
            BUILD_QUEUE.addLast(id);
        }
    }

    /** 该模型几何是否已就绪。 */
    private static boolean isReady(@Nullable ResourceLocation id) {
        return id != null && BUILD_DONE.contains(id);
    }

    /**
     * 按预算推进构建队列。
     *
     * <p>直接调 {@code GeckoLibCache#getGeoModel} 会触发按需构建并缓存结果，
     * 所以这里不用自己解析 JSON；动画文件一并构建，免得渲染时又踩一次。</p>
     */
    private static void pumpBuildQueue() {
        long deadline = System.nanoTime() + BUILD_BUDGET_NANOS;
        int built = 0;
        while (!BUILD_QUEUE.isEmpty() && built < MAX_BUILDS_PER_FRAME && System.nanoTime() < deadline) {
            ResourceLocation id = BUILD_QUEUE.pollFirst();
            if (id == null || BUILD_DONE.contains(id)) {
                continue;
            }
            GeckoLibCache cache = GeckoLibCache.getInstance();
            // 构建失败（资源缺失）也记为已处理，否则会每帧重试同一个坏条目
            cache.getGeoModel(id);
            cache.getAnimationFile(id);
            BUILD_DONE.add(id);
            built++;
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        pumpBuildQueue();
        super.render(poseStack, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void drawLeftEntity(int middleX, int middleY, float mouseX, float mouseY) {
        ResourceLocation current = ResourceLocation.tryParse(this.entity.getModelId());
        // 几何没好就先不画，否则渲染器会拿 null 模型并刷警告
        if (!isReady(current)) {
            enqueueModel(current, true);
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
            enqueueModel(id, false);
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
        // 目标模型排到队首，左侧立绘就绪后立刻切过去
        enqueueModel(modelItem.getModelId(), true);
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

    /** 诊断用：队列里还剩多少个模型没构建。 */
    public static int pendingBuilds() {
        return BUILD_QUEUE.size();
    }

    /** 供外部（例如命令）预构建全部模型。 */
    public static void enqueueAll() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (MaidModelInfo info : MaidModelRegistry.all()) {
            ids.add(info.getModelId());
        }
        for (ResourceLocation id : ids) {
            enqueueModel(id, false);
        }
    }
}

