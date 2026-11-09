package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.event.client.OpenPatchouliBookEvent;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.client.event.PlayerLoggedInNotice;
import com.github.tartaricacid.touhoulittlemaid.client.screen.widget.MaidSideTabButton;
import com.github.tartaricacid.touhoulittlemaid.client.screen.widget.MaidTabButton;
import com.github.tartaricacid.touhoulittlemaid.client.screen.widget.TaskButton;
import com.github.tartaricacid.touhoulittlemaid.compat.patchouli.PatchouliCompat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidEffectData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.TabIndex;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.MaidMainContainer;
import com.github.tartaricacid.touhoulittlemaid.network.MaidConfigPacket;
import com.github.tartaricacid.touhoulittlemaid.client.resource.MaidModelRegistry;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 女仆界面基类，移植自上游 {@code client/gui/entity/maid/AbstractMaidContainerGui}。
 *
 * <p>三个页签（主界面 / 任务设置 / 女仆配置）共用同一张底图与同一套外围控件：
 * 女仆立绘、四条状态条、右侧顶部页签、左侧任务列表面板、下方玩家物品栏。
 * 各页签只覆写 {@link #renderAddition} 与 {@link #initAdditionWidgets}。</p>
 *
 * <p><b>页签是怎么切过去的：</b>不是换内容，而是<b>换界面</b> ——
 * 点页签只发一个包，服务端关掉当前菜单、按新的 tab 打开另一个菜单
 * （见 {@code EntityMaid#openMaidGui(Player, int)}）。所以本类只负责画按钮，
 * 不做任何跳转逻辑。</p>
 *
 * <p><b>关键版式约定（踩过坑）：</b></p>
 * <ul>
 *     <li>{@code renderBg} 里的 {@link PoseStack} <b>没有</b>被平移到 GUI 原点，
 *         所以纹理绘制一律用 {@code leftPos + x} 绝对坐标；</li>
 *     <li>{@code renderLabels} 里的 <b>已经</b>平移过，因此文字用相对坐标；</li>
 *     <li>1.18.2 的 {@code AbstractContainerScreen#render} 既不代劳
 *         {@code renderBackground} 也不代劳 4 参 {@code renderTooltip}，
 *         两者都必须自己调，否则「背景不变暗 / 悬停没提示」；</li>
 *     <li>Widget 永远画在 {@code renderBg} 之上，所以「当前任务图标与名称」必须放到
 *         {@code super.render(...)} 之后画，否则会被任务开关按钮的底图盖掉。</li>
 * </ul>
 */
public abstract class AbstractMaidScreen<T extends AbstractMaidContainer> extends AbstractContainerScreen<T> {
    protected static final ResourceLocation BG =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/maid_gui_main.png");
    protected static final ResourceLocation SIDE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/maid_gui_side.png");
    protected static final ResourceLocation BUTTON =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/maid_gui_button.png");
    protected static final ResourceLocation TASK =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/maid_gui_task.png");

    /**
     * 四条状态条的纵向位置（相对 GUI 原点），与上游 {@code drawBaseInfoGui} 一致。
     *
     * <p>顺序是 血量 / 护甲 / 经验 / 好感度 —— 与上游逐行对应，
     * 前三项的图标分别是 {@code (0,0) (9,0) (18,0)}，好感度是 {@code (27,0)}。</p>
     */
    protected static final int[] BAR_Y = {113, 124, 135, 146};
    /**
     * 数字标签的位置（相对 GUI 原点）。
     */
    private static final int NUMBER_X = 63;
    /**
     * 任务列表每页 12 条（上游 {@code TASK_COUNT_PER_PAGE}）：
     * 行高 19，首行相对 Y=23，最后一行到 232+19=251，正好填满 92x251 的面板。
     */
    private static final int TASK_COUNT_PER_PAGE = 12;

    /**
     * 当前页与展开状态。
     *
     * <p>上游把这两个字段做成 {@code static}，意义是「关掉界面再打开时保持上次的样子」。
     * 这里保持一致。两者只存在于客户端类里，不会被服务端加载。</p>
     */
    protected static int TASK_PAGE = 0;
    protected static boolean TASK_LIST_OPEN = false;

    /**
     * 三个页签的 UV 左边界与显示名（{@code maid_gui_side.png} 里的图标行）。
     */
    private static final int[] TAB_UV = {107, 132, 157};
    private static final String[] TAB_KEYS = {"main", "task_config", "maid_config"};
    private static final int[] TAB_INDEX = {TabIndex.MAIN, TabIndex.TASK_CONFIG, TabIndex.MAID_CONFIG};

    @Nullable
    protected final EntityMaid maid;

    private StateSwitchingButton homeButton;
    private StateSwitchingButton pickButton;
    private StateSwitchingButton rideButton;
    private ImageButton taskSwitchButton;
    private ImageButton pageUpButton;
    private ImageButton pageDownButton;
    private ImageButton pageCloseButton;
    private ImageButton infoButton;
    /**
     * 右侧侧边栏的「任务手册」按钮（帕秋莉手册入口）。
     *
     * <p>与上游 {@code MaidSideTabs} 一样，未安装 Patchouli 时按钮<b>仍然显示</b>，
     * 点了才提示缺少前置 —— 藏起来反而让人以为功能不存在。</p>
     */
    @Nullable
    private MaidSideTabButton taskBookButton;

    /**
     * 全部任务按钮（一次建完，靠 {@code visible} 分页）。
     *
     * <p>上游的做法是「点翻页就 {@code init()} 重建一遍 widget」。
     * 那样必须保证 {@code clearWidgets()} 一定被调用，否则 widget 会越堆越多、
     * 点击事件也会重复派发。这里改成「全建 + 按页切 {@code visible}」，
     * 少一个生命周期依赖，行为完全一样。</p>
     */
    private final List<TaskButton> taskButtons = new ArrayList<>();
    private final List<MaidTabButton> tabButtons = new ArrayList<>();

    /**
     * 上一次看到的「服务端同步值」，用来判断同步数据是否真的发生了变化。
     *
     * <p>不能在每帧都无条件回写按钮状态：点击时本地先乐观翻转，包走到服务端再同步回来，
     * 中间有一两个 tick 的空档，逐帧回写会让按钮「闪回」一下。</p>
     */
    private boolean lastSyncedHome;
    private boolean lastSyncedPickup;
    private boolean lastSyncedRideable;

    /**
     * 状态效果的刷新计时器，每 20 tick（1 秒）向服务端拉一次。
     *
     * <p>用界面自己的计数而不是 {@code maid.tickCount}：换页签会重建 Screen，
     * 两个界面在同一个 tick 里各发一次请求并不会「合并」，但也没必要为这个再引入共享状态。</p>
     */
    private int effectTick;

    protected AbstractMaidScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = MaidMainContainer.GUI_WIDTH;
        this.imageHeight = MaidMainContainer.GUI_HEIGHT;
        this.maid = menu.getMaid();
        // 上游不画标题与「物品栏」标签，全部由贴图承担
        this.titleLabelX = -9999;
        this.inventoryLabelX = -9999;
    }

    /**
     * 当前界面是第几个页签（用来把对应按钮画成「已选中」）。
     */
    protected abstract int getCurrentTab();

    /**
     * 页签自己的额外内容（画在底图之上、控件之下）。
     *
     * <p>坐标是<b>绝对</b>的：{@code renderBg} 里 PoseStack 没有被平移。</p>
     */
    protected void renderAddition(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
    }

    /**
     * 页签自己的控件（在家/拾取/页签/任务列表之后创建）。
     */
    protected void initAdditionWidgets() {
    }

    @Override
    protected void init() {
        super.init();
        if (this.maid == null) {
            return;
        }

        // 家（居家模式）
        this.homeButton = new StateSwitchingButton(this.leftPos + 9, this.topPos + 206, 20, 20,
                this.maid.isHomeModeEnable()) {
            @Override
            public void onClick(double mouseX, double mouseY) {
                this.isStateTriggered = !this.isStateTriggered;
                MaidClientPacketSender.sendConfig(MaidConfigPacket.HOME, this.isStateTriggered, maid.getId());
            }
        };
        this.homeButton.initTextureValues(0, 0, 21, 21, BUTTON);
        this.addRenderableWidget(this.homeButton);
        this.lastSyncedHome = this.maid.isHomeModeEnable();

        // 拾取
        this.pickButton = new StateSwitchingButton(this.leftPos + 30, this.topPos + 206, 20, 20,
                this.maid.isPickup()) {
            @Override
            public void onClick(double mouseX, double mouseY) {
                this.isStateTriggered = !this.isStateTriggered;
                MaidClientPacketSender.sendConfig(MaidConfigPacket.PICKUP, this.isStateTriggered, maid.getId());
            }
        };
        this.pickButton.initTextureValues(42, 0, 21, 21, BUTTON);
        this.addRenderableWidget(this.pickButton);
        this.lastSyncedPickup = this.maid.isPickup();

        // 骑乘（能否被抱起 / 坐上船车）。上游 UV 是 (84, 0)，位置 leftPos+51 —— 紧跟在拾取右边。
        // 它同时也是「用鞍抱起女仆」的总开关：关掉后鞍只会在服务端被拦下，不做任何事。
        this.rideButton = new StateSwitchingButton(this.leftPos + 51, this.topPos + 206, 20, 20,
                this.maid.isRideable()) {
            @Override
            public void onClick(double mouseX, double mouseY) {
                this.isStateTriggered = !this.isStateTriggered;
                MaidClientPacketSender.sendConfig(MaidConfigPacket.RIDEABLE, this.isStateTriggered, maid.getId());
            }
        };
        this.rideButton.initTextureValues(84, 0, 21, 21, BUTTON);
        this.addRenderableWidget(this.rideButton);
        this.lastSyncedRideable = this.maid.isRideable();

        // 详细信息按钮（蓝色感叹号）。上游 AbstractMaidContainerGui#addStateButton 里的 `info`，
        // 位置与 UV 逐像素一致：leftPos+8, topPos+14，9x9，UV(72,65)，hover 下移 10。
        // 上游给它挂的是 NO_ACTION —— 点它不做任何事，功能全在悬停提示里（见 renderMaidInfo）。
        this.infoButton = new ImageButton(this.leftPos + 8, this.topPos + 14, 9, 9,
                72, 65, 10, BUTTON, b -> {
        });
        this.addRenderableWidget(this.infoButton);

        // 皮肤 / 模型按钮：上游 CacheIconManager.openMaidModelGui，本移植版直接开界面
        ImageButton skinButton = new ImageButton(this.leftPos + 62, this.topPos + 14, 9, 9,
                72, 43, 10, BUTTON,
                b -> MaidModelGui.open(this.maid));
        this.addRenderableWidget(skinButton);

        // 任务列表：开关 + 翻页 + 全部任务按钮
        this.taskButtons.clear();
        this.addTaskSwitchButton();
        this.addTaskControlButtons();
        this.addTaskListButtons();

        // 顶部三个页签
        this.tabButtons.clear();
        this.addTabButtons();

        // 右侧侧边栏（帕秋莉手册入口）
        this.addSideTabButtons();

        this.initAdditionWidgets();
    }

    // ------------------------------------------------------------------ 页签

    /**
     * 顶部三个页签按钮。
     *
     * <p>「当前页签」的做法与上游一致：把按钮的 {@code active} 置 false，
     * 按钮就会画上那张「已按下」的底图，并且不再响应点击 / 提示。</p>
     */
    private void addTabButtons() {
        int currentTab = this.getCurrentTab();
        for (int i = 0; i < TAB_UV.length; i++) {
            MaidTabButton button = new MaidTabButton(this.leftPos + 94 + 25 * i, this.topPos + 5,
                    TAB_UV[i], TAB_INDEX[i], currentTab, this.maid.getId(), TAB_KEYS[i]);
            this.tabButtons.add(button);
            this.addRenderableWidget(button);
        }
    }

    // --------------------------------------------------- 右侧侧边栏（手册入口）

    /**
     * 右侧侧边栏：目前只有「任务手册」一个按钮。
     *
     * <p>位置与上游一致：{@code leftPos + 251, topPos + 37}，按钮 26x24，
     * 图标由 {@link MaidSideTabButton} 从 {@code maid_gui_side.png} 取。</p>
     */
    private void addSideTabButtons() {
        this.taskBookButton = new MaidSideTabButton(this.leftPos + 251, this.topPos + 37, 0,
                List.of(new TranslatableComponent("gui.touhou_little_maid.button.task_book"),
                        new TranslatableComponent("gui.touhou_little_maid.button.task_book.desc")),
                b -> this.openTaskBook());
        this.addRenderableWidget(this.taskBookButton);
    }

    /**
     * 点「任务手册」按钮。
     *
     * <p>装了 Patchouli：发一个 {@link OpenPatchouliBookEvent}，由
     * {@code compat/patchouli/OpenDefaultBook} 打开本模组的手册；其它模组可以监听同一事件
     * 给自己的任务打开自己的书。</p>
     *
     * <p>没装 Patchouli：在聊天栏给出与上游登录提示同款的「点这里安装」消息
     * （上游这里弹出的是警告界面，本移植版按需求改为聊天栏，信息一字不差）。</p>
     */
    private void openTaskBook() {
        if (this.maid == null) {
            return;
        }
        if (!PatchouliCompat.isLoaded()) {
            PlayerLoggedInNotice.sendMissingPatchouliMessage(Minecraft.getInstance().player);
            return;
        }
        MinecraftForge.EVENT_BUS.post(new OpenPatchouliBookEvent(this.maid, this.maid.getTask()));
    }

    // ------------------------------------------------------- 任务列表（左侧面板）

    /**
     * 左下角「当前任务」整块可点击，点击即展开 / 收起左侧任务列表。
     */
    private void addTaskSwitchButton() {
        this.taskSwitchButton = new ImageButton(this.leftPos + 4, this.topPos + 159, 71, 21,
                0, 42, 22, BUTTON, b -> {
            TASK_LIST_OPEN = !TASK_LIST_OPEN;
            this.updateTaskPanelVisibility();
        });
        this.addRenderableWidget(this.taskSwitchButton);
    }

    /**
     * 面板顶部的三个小按钮：上一页 (110,0) / 下一页 (93,0) / 关闭 (127,0)。
     */
    private void addTaskControlButtons() {
        this.pageUpButton = new ImageButton(this.leftPos - 89, this.topPos + 9, 16, 13,
                110, 0, 14, TASK, b -> {
            if (TASK_PAGE > 0) {
                TASK_PAGE--;
                this.updateTaskPanelVisibility();
            }
        });
        this.pageDownButton = new ImageButton(this.leftPos - 72, this.topPos + 9, 16, 13,
                93, 0, 14, TASK, b -> {
            if ((TASK_PAGE + 1) * TASK_COUNT_PER_PAGE < TaskManager.getTaskIndex().size()) {
                TASK_PAGE++;
                this.updateTaskPanelVisibility();
            }
        });
        this.pageCloseButton = new ImageButton(this.leftPos - 19, this.topPos + 9, 13, 13,
                127, 0, 14, TASK, b -> {
            TASK_LIST_OPEN = false;
            this.updateTaskPanelVisibility();
        });
        this.addRenderableWidget(this.pageUpButton);
        this.addRenderableWidget(this.pageDownButton);
        this.addRenderableWidget(this.pageCloseButton);
    }

    private void addTaskListButtons() {
        List<IMaidTask> tasks = TaskManager.getTaskIndex();
        if (TASK_PAGE * TASK_COUNT_PER_PAGE >= tasks.size()) {
            TASK_PAGE = 0;
        }
        for (int index = 0; index < tasks.size(); index++) {
            IMaidTask maidTask = tasks.get(index);
            boolean enable = maidTask.isEnable(this.maid);
            int row = index % TASK_COUNT_PER_PAGE;
            TaskButton button = new TaskButton(this.leftPos - 89, this.topPos + 23 + 19 * row,
                    maidTask, enable, TASK, this.getTaskTooltips(maidTask),
                    b -> MaidClientPacketSender.sendTask(maidTask.getUid(), this.maid.getId()));
            this.taskButtons.add(button);
            this.addRenderableWidget(button);
        }
        this.updateTaskPanelVisibility();
    }

    /**
     * 按当前展开状态与页码刷新可见性。
     *
     * <p>翻页与收起都只动 {@code visible}，不重建 widget —— 见
     * {@link #taskButtons} 的注释。</p>
     */
    private void updateTaskPanelVisibility() {
        if (this.pageUpButton != null) {
            this.pageUpButton.visible = TASK_LIST_OPEN;
        }
        if (this.pageDownButton != null) {
            this.pageDownButton.visible = TASK_LIST_OPEN;
        }
        if (this.pageCloseButton != null) {
            this.pageCloseButton.visible = TASK_LIST_OPEN;
        }
        if (this.taskSwitchButton != null) {
            this.taskSwitchButton.visible = true;
        }
        for (int index = 0; index < this.taskButtons.size(); index++) {
            this.taskButtons.get(index).visible =
                    TASK_LIST_OPEN && index / TASK_COUNT_PER_PAGE == TASK_PAGE;
        }
    }

    /**
     * 任务悬停提示，移植自上游 {@code getTaskTooltips}。
     *
     * <p>上游用了 {@code ParseI18n.keysToTrans} 把「已有的译文」再转成 Component；
     * 这里不需要那一步，直接把 key 交给 {@link TranslatableComponent}，
     * 效果相同（也顺带支持语言热切换）。</p>
     */
    private List<Component> getTaskTooltips(IMaidTask maidTask) {
        List<Component> desc = new ArrayList<>();
        for (String key : maidTask.getDescription(this.maid)) {
            desc.add(new TranslatableComponent(key).withStyle(ChatFormatting.GRAY));
        }
        if (!desc.isEmpty()) {
            desc.add(0, new TranslatableComponent("task.touhou_little_maid.desc.title")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (!maidTask.isEnable(this.maid)) {
            desc.add(new TextComponent(" "));
            desc.add(new TranslatableComponent("task.touhou_little_maid.desc.enable_condition")
                    .withStyle(ChatFormatting.GOLD));
            for (Pair<String, Predicate<EntityMaid>> line : maidTask.getEnableConditionDesc(this.maid)) {
                desc.add(this.buildConditionLine(maidTask, "enable_condition", line));
            }
        }
        List<Pair<String, Predicate<EntityMaid>>> conditions = maidTask.getConditionDescription(this.maid);
        if (!conditions.isEmpty()) {
            desc.add(new TextComponent(" "));
            desc.add(new TranslatableComponent("task.touhou_little_maid.desc.condition")
                    .withStyle(ChatFormatting.GOLD));
            for (Pair<String, Predicate<EntityMaid>> line : conditions) {
                desc.add(this.buildConditionLine(maidTask, "condition", line));
            }
        }
        return desc;
    }

    /**
     * 生成一行「- 条件说明」，满足为绿、未满足为红。
     *
     * <p>key 的拼法与上游一致：{@code task.<ns>.<path>.<kind>.<name>}，
     * 其中 {@code kind} 是 {@code enable_condition} 或 {@code condition}。</p>
     */
    private Component buildConditionLine(IMaidTask maidTask, String kind,
                                         Pair<String, Predicate<EntityMaid>> line) {
        String key = String.format("task.%s.%s.%s.%s", maidTask.getUid().getNamespace(),
                maidTask.getUid().getPath(), kind, line.getFirst());
        MutableComponent condition = new TranslatableComponent(key)
                .withStyle(line.getSecond().test(this.maid) ? ChatFormatting.GREEN : ChatFormatting.RED);
        return new TextComponent("-\u0020").append(condition);
    }

    // ------------------------------------------------------------------ 绘制

    /**
     * 渲染入口。
     *
     * <p>顺序固定为：压暗世界 → 界面本体（{@code super.render} 内部会调 {@code renderBg}）
     * → 当前任务图标与名称 → 槽位物品提示 → 按钮说明文字。Tooltip 必须最后，
     * 否则会被界面元件盖住。</p>
     */
    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        if (this.maid == null) {
            return;
        }
        this.syncButtonState();

        // 世界压暗层。1.18.2 的 AbstractContainerScreen#render 不会替我们调
        // renderBackground，所以必须自己来，并且要在 super.render 之前画，
        // 这样界面贴图才会盖在压暗层之上。
        this.renderBackground(poseStack);

        super.render(poseStack, mouseX, mouseY, partialTick);

        // 左侧状态效果列表（上游 drawEffectInfo）：画在界面之外，必须在 super.render 之后
        this.drawEffectInfo(poseStack);

        // 当前任务的图标与名称：必须晚于 super.render，否则会被 taskSwitch 按钮底图盖住
        this.drawCurrentTaskText(poseStack);

        // 槽位物品提示（4 参重载）与按钮说明文字都必须在这里手动补
        super.renderTooltip(poseStack, mouseX, mouseY);
        this.renderButtonTooltips(poseStack, mouseX, mouseY);
    }

    /**
     * 按钮显示状态以服务端同步值（SynchedEntityData）为准。
     *
     * <p>只在同步值「发生变化」时回写 —— 否则点击后本地乐观翻转会被立刻抹掉。</p>
     */
    private void syncButtonState() {
        if (this.homeButton != null) {
            boolean home = this.maid.isHomeModeEnable();
            if (home != this.lastSyncedHome) {
                this.homeButton.setStateTriggered(home);
                this.lastSyncedHome = home;
            }
        }
        if (this.pickButton != null) {
            boolean pickup = this.maid.isPickup();
            if (pickup != this.lastSyncedPickup) {
                this.pickButton.setStateTriggered(pickup);
                this.lastSyncedPickup = pickup;
            }
        }
        if (this.rideButton != null) {
            boolean rideable = this.maid.isRideable();
            if (rideable != this.lastSyncedRideable) {
                this.rideButton.setStateTriggered(rideable);
                this.lastSyncedRideable = rideable;
            }
        }
    }

    /**
     * 界面打开期间每秒向服务端要一次状态效果。
     *
     * <p><b>为什么必须主动问：</b>原版只把<b>玩家自己</b>的效果下发给客户端
     * （{@code ClientPacketListener#handleUpdateMobEffect} 的接收方写死 {@code LocalPlayer}），
     * 别的实体挂什么效果客户端永远收不到，{@code maid.getActiveEffects()} 在客户端恒为空。</p>
     *
     * <p>上游 {@code AbstractMaidContainerGui#containerTick} 同样是 {@code % 20 == 0}。</p>
     */
    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.maid == null) {
            return;
        }
        if (++this.effectTick % 20 == 0) {
            MaidClientPacketSender.requestEffect(this.maid.getId());
        }
    }

    /**
     * 把女仆身上的状态效果画在界面<b>左侧外面</b>（上游 {@code drawEffectInfo}）。
     *
     * <p>任务列表展开时直接不画 —— 那块面板正好占据 {@code leftPos - 93 .. leftPos - 1}，
     * 与文字位置完全重叠。上游也是这么处理的。</p>
     *
     * <p>每行格式与上游逐字一致：{@code 效果名 [等级] 剩余时间}。
     * 等级只在 1~9<i>（即 II~X 级）</i>时显示，与药水提示框的规则相同；
     * 时间用原版 {@code StringUtil.formatTickDuration}，因此是 {@code 1:23} 这种格式。</p>
     *
     * <p>坐标是<b>绝对</b>的：它在 {@code render()} 里被调用，PoseStack 没有被平移到 GUI 原点。</p>
     */
    private void drawEffectInfo(PoseStack poseStack) {
        if (TASK_LIST_OPEN) {
            return;
        }
        List<MaidEffectData> effects = this.maid.getEffects();
        int yOffset = 5;
        for (MaidEffectData effect : effects) {
            MutableComponent text = new TranslatableComponent(effect.descriptionId());
            if (effect.amplifier() >= 1 && effect.amplifier() <= 9) {
                text = text.append(" ").append(new TranslatableComponent("enchantment.level." + (effect.amplifier() + 1)));
            }
            String duration = effect.duration() == -1
                    ? I18n.get("effect.duration.infinite")
                    : StringUtil.formatTickDuration(effect.duration());
            text = text.append(" ").append(duration);
            this.font.draw(poseStack, text, this.leftPos - this.font.width(text) - 3,
                    this.topPos + yOffset + 5, getPotionColor(effect.category()));
            yOffset += 10;
        }
    }

    /**
     * 按效果的正负面着色：正面绿、负面红、中立蓝。
     *
     * <p>上游直接 {@code switch (category)} 写 0/1/2。这里改用
     * {@link MobEffectCategory#ordinal()} 比较，免得哪天原版调整枚举顺序时变成「药水全变绿」。</p>
     */
    private static int getPotionColor(int category) {
        if (category == MobEffectCategory.BENEFICIAL.ordinal()) {
            return ChatFormatting.GREEN.getColor();
        }
        if (category == MobEffectCategory.HARMFUL.ordinal()) {
            return ChatFormatting.RED.getColor();
        }
        return ChatFormatting.BLUE.getColor();
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        // 主背景
        RenderSystem.setShaderTexture(0, BG);
        this.blit(poseStack, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);

        // 女仆立绘
        this.drawMaidCharacter(mouseX, mouseY);

        // 四条状态条 + 任务按钮底槽
        this.drawStatusBars(poseStack);

        // 页签自己的内容（背包格 / 配置行 / 任务设置面板）
        this.renderAddition(poseStack, mouseX, mouseY, partialTicks);

        // 左侧任务列表面板（画在最后，盖住其下的一切）
        this.drawTaskListBg(poseStack);

        // 模组贴图的空槽提示（只有主界面有这种槽位）
        this.drawManualSlotHints(poseStack);

        // 右侧侧边栏外框（帕秋莉手册入口）
        this.drawSideTabBg(poseStack);
    }

    /**
     * 左侧任务列表面板底图（上游 {@code drawTaskListBg}）：{@code maid_gui_task.png} 的 (0,0) 92x251。
     *
     * <p>面板在 {@code leftPos - 93 .. leftPos - 1}，完全位于主界面左侧之外，
     * 所以画在主背景之后也不会互相遮挡。</p>
     */
    private void drawTaskListBg(PoseStack poseStack) {
        if (!TASK_LIST_OPEN) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TASK);
        this.blit(poseStack, this.leftPos - 93, this.topPos + 5, 0, 0, 92, 251);
    }

    /**
     * 右侧侧边栏的外框。
     *
     * <p>贴图里这是一整块 21x50、上下两个槽位的框；本移植版只实现了一个手册按钮，
     * 所以只画上面那一格 —— 贴图在 {@code (235,107)} 处正好是一个完整的 21x25 圆角框，
     * 不会出现「下半截空槽」的残缺感。</p>
     */
    private void drawSideTabBg(PoseStack poseStack) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SIDE);
        this.blit(poseStack, this.leftPos + 256, this.topPos + 37, 235, 107, 21, 25);
    }

    /**
     * 当前任务的图标与名称（上游 {@code drawCurrentTaskText}）。
     *
     * <p>用<b>绝对</b>坐标 —— 因为它在 {@code render()} 里调用，PoseStack 没有被平移。</p>
     */
    private void drawCurrentTaskText(PoseStack poseStack) {
        IMaidTask task = this.maid.getTask();

        float blitOffset = this.itemRenderer.blitOffset;
        this.itemRenderer.blitOffset = 10.0F;
        this.itemRenderer.renderGuiItem(task.getIcon(), this.leftPos + 6, this.topPos + 161);
        this.itemRenderer.blitOffset = blitOffset;

        List<FormattedCharSequence> splitTexts = this.font.split(task.getName(), 42);
        if (!splitTexts.isEmpty()) {
            this.font.draw(poseStack, splitTexts.get(0), this.leftPos + 28, this.topPos + 165, 0x333333);
        }
    }

    private void drawMaidCharacter(int mouseX, int mouseY) {
        if (this.maid == null) {
            return;
        }
        double scale = this.minecraft == null ? 1.0D : this.minecraft.getWindow().getGuiScale();
        RenderSystem.enableScissor(
                (int) ((this.leftPos + 6) * scale),
                (int) ((this.topPos + 107 + 42) * scale),
                (int) (67 * scale),
                (int) (95 * scale));
        InventoryScreen.renderEntityInInventory(this.leftPos + 40, this.topPos + 100, 40,
                (float) (this.leftPos + 40 - mouseX), (float) (this.topPos + 70 - 20 - mouseY), this.maid);
        RenderSystem.disableScissor();
    }

    /**
     * 四条状态条：血量 / 护甲 / 经验 / 好感度（上游 {@code drawBaseInfoGui}）。
     *
     * <p>这里只画贴图，数字统一交给 {@link #renderLabels}（那里的坐标系才是相对的）。</p>
     */
    private void drawStatusBars(PoseStack poseStack) {
        if (this.maid == null) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SIDE);

        // 图标（左侧 9x9）+ 底槽（47x9）
        this.blit(poseStack, this.leftPos + 53, this.topPos + BAR_Y[0], 0, 0, 9, 9);
        this.blit(poseStack, this.leftPos + 5, this.topPos + BAR_Y[0], 0, 9, 47, 9);
        this.blit(poseStack, this.leftPos + 53, this.topPos + BAR_Y[1], 9, 0, 9, 9);
        this.blit(poseStack, this.leftPos + 5, this.topPos + BAR_Y[1], 0, 9, 47, 9);
        this.blit(poseStack, this.leftPos + 53, this.topPos + BAR_Y[2], 18, 0, 9, 9);
        this.blit(poseStack, this.leftPos + 5, this.topPos + BAR_Y[2], 0, 9, 47, 9);
        this.blit(poseStack, this.leftPos + 53, this.topPos + BAR_Y[3], 27, 0, 9, 9);
        this.blit(poseStack, this.leftPos + 5, this.topPos + BAR_Y[3], 0, 9, 47, 9);

        double expPercent = (this.maid.getExperience() % 120) / 120.0D;

        // 填充条（内部 43x5，起点相对 +2/+2）
        this.blit(poseStack, this.leftPos + 7, this.topPos + BAR_Y[0] + 2, 2, 18,
                (int) (43 * clamp01(this.maid.getHealth() / this.maid.getMaxHealth())), 5);
        this.blit(poseStack, this.leftPos + 7, this.topPos + BAR_Y[1] + 2, 2, 23,
                (int) (43 * clamp01(this.maid.getAttributeValue(Attributes.ARMOR) / 20.0D)), 5);
        this.blit(poseStack, this.leftPos + 7, this.topPos + BAR_Y[2] + 2, 2, 28,
                (int) (43 * clamp01(expPercent)), 5);
        this.blit(poseStack, this.leftPos + 7, this.topPos + BAR_Y[3] + 2, 2, 33,
                (int) (43 * clamp01(this.maid.getFavorabilityManager().getLevelPercent())), 5);

        // 顶部页签底槽与左下任务按钮底槽
        this.blit(poseStack, this.leftPos + 94, this.topPos + 7, 107, 0, 149, 21);
        this.blit(poseStack, this.leftPos + 6, this.topPos + 178, 0, 47, 67, 25);
    }

    /**
     * 为「模组自带贴图」的空槽补画提示图标。
     *
     * <p>原版 {@code AbstractContainerScreen#renderSlot} 只在「槽位为空 + 槽位启用 +
     * {@code Slot#getNoItemIcon()} 非空」时自动画一次提示图，而那条路要求贴图
     * <b>已经并入图集</b>（原因见 {@code MaidMainContainer#EMPTY_MAINHAND_SLOT}）。
     * 模组贴图进不了图集，所以这两格只能自己画。</p>
     *
     * <p><b>为什么画在 {@code renderBg} 里而不是重写 {@code renderSlot}：</b>
     * 1.18.2 的 {@code AbstractContainerScreen#renderSlot} 是 <b>private</b> 的
     * （到 1.19 才放开），根本没法重写。好在槽位为空时那一格里没有任何东西要画，
     * 所以只要盖在界面底图之上，位置就等于原版的行为。</p>
     *
     * <p>坐标系：{@code renderBg} 里的 PoseStack 没有被平移到 GUI 原点，
     * 因此这里必须用 {@code leftPos + slot.x} 的绝对坐标。</p>
     */
    private void drawManualSlotHints(PoseStack poseStack) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        for (Slot slot : this.menu.slots) {
            if (!(slot instanceof MaidMainContainer.HintSlot hintSlot)) {
                continue;
            }
            ResourceLocation hint = hintSlot.getHintTexture();
            if (hint == null || !slot.isActive() || !slot.getItem().isEmpty()) {
                continue;
            }
            RenderSystem.setShaderTexture(0, hint);
            // 16x16 的小图，必须显式给出贴图尺寸，否则会被当成 256x256 拉伸
            blit(poseStack, this.leftPos + slot.x, this.topPos + slot.y, 0.0F, 0.0F, 16, 16, 16, 16);
        }
    }

    // ------------------------------------------------------------------ 文字

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        if (this.maid == null) {
            return;
        }
        // 版本号（上游 drawModInfo 的等价物）
        String debugInfo = "1.18.2-futo.1";
        this.font.draw(poseStack, debugInfo, 3.0F, -10.0F, 0xFFFFFF);

        // 四条状态数字（0.5 缩放），顺序与 drawStatusBars 一致
        this.drawNumber(poseStack, (int) Math.ceil(this.maid.getHealth()), NUMBER_X, BAR_Y[0] + 1);
        this.drawNumber(poseStack, this.maid.getArmorValue(), NUMBER_X, BAR_Y[1] + 1);
        this.drawNumber(poseStack, (int) (this.maid.getExperience() / 120), NUMBER_X, BAR_Y[2] + 1);
        this.drawNumber(poseStack, this.maid.getFavorability(), NUMBER_X, BAR_Y[3] + 1);

        // 任务列表页码（上游 drawTaskPageCount，相对坐标 -48/12）
        if (TASK_LIST_OPEN) {
            int totalPages = Math.max(1,
                    (TaskManager.getTaskIndex().size() + TASK_COUNT_PER_PAGE - 1) / TASK_COUNT_PER_PAGE);
            String text = String.format("%d/%d", TASK_PAGE + 1, totalPages);
            this.font.draw(poseStack, text, -48.0F, 12.0F, 0x333333);
        }
    }

    /**
     * 以 0.5 倍缩放绘制两位数。
     *
     * <p>因为 {@code renderLabels} 的坐标系已经是相对 GUI 原点，这里只需把坐标乘 2
     * 抵消缩放，<b>不要再叠加 leftPos/topPos</b>。</p>
     */
    private void drawNumber(PoseStack poseStack, int value, int relX, int relY) {
        String text = String.format("%02d", Math.max(0, value));
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 1.0F);
        this.font.draw(poseStack, text, relX * 2, relY * 2 + this.font.lineHeight / 2,
                ChatFormatting.DARK_GRAY.getColor());
        poseStack.popPose();
    }

    // ------------------------------------------------------------------ 提示

    /**
     * 按钮说明文字。
     *
     * <p>不用 {@code isHoveredOrFocused()}：那个值只在 {@code AbstractWidget#render}
     * 里被刷新，容器渲染顺序一旦变动就会读到旧值。这里直接拿鼠标坐标与按钮包围盒比较。</p>
     */
    /**
     * 详细信息提示：逐项对齐上游 {@code AbstractMaidContainerGui#renderMaidInfo}。
     *
     * <p>上游的格式是「标题行（金色下划线）+ 若干 <b>█</b> 开头的条目」，条目里字段名青色、
     * 值白色。标题行后面还会追加两个状态符号：</p>
     *
     * <ul>
     *     <li>{@code ❀}（暗红）—— 被闪电击中过（{@code isStruckByLightning()}）</li>
     *     <li>{@code ✟}（蓝色）—— 无敌（{@code isInvulnerable()}）</li>
     * </ul>
     *
     * <p>条目的取舍：</p>
     * <ul>
     *     <li><b>主人</b>：上游用 {@code maid.getOwner()}，客户端可能取不到实体，所以只在非空时加；
     *         本移植版更稳一点 —— 先试 {@code getOwner()}，取不到但 UUID 是本地玩家就用本地名字，
     *         否则退回 UUID 前 8 位，避免出现「主人：null」。</li>
     *     <li><b>模型名</b>：走 {@code MaidModelRegistry}。旧版模型的 {@code name} 字段是
     *         {@code {model.ns.path.name}} 这种带花括号的翻译键，要按上游
     *         {@code ParseI18n.parse} 的语义处理 —— 见 {@link #parseModelName}。</li>
     *     <li><b>经验 / 好感度</b>：直接读同步字段。</li>
     *     <li><b>五子棋战绩</b>：依赖尚未移植的 {@code GameRecordManager} 与
     *         {@code MaidGomokuAI}，这里整行省略（不显示空数据比显示 0 更诚实）。</li>
     * </ul>
     *
     * <p>用鼠标坐标判断悬停而不是 {@code isHoveredOrFocused()}，理由同
     * {@link #renderButtonTooltips}。</p>
     */
    private void renderMaidInfo(PoseStack poseStack, int mouseX, int mouseY) {
        if (this.infoButton == null || !isMouseOver(this.infoButton, mouseX, mouseY)) {
            return;
        }
        List<Component> list = new ArrayList<>();
        // 上游的前缀：一个绿色方块 + 空格
        String prefix = ChatFormatting.GREEN + "\u2588\u0020";

        MutableComponent title = new TextComponent("")
                .append(new TranslatableComponent("tooltips.touhou_little_maid.info.title")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.UNDERLINE))
                .append(new TextComponent("  "));
        if (this.maid.isStruckByLightning()) {
            title.append(new TextComponent("\u2740").withStyle(ChatFormatting.DARK_RED));
        }
        if (this.maid.isInvulnerable()) {
            title.append(new TextComponent("\u271F").withStyle(ChatFormatting.BLUE));
        }
        list.add(title);

        Component ownerName = this.resolveOwnerName();
        if (ownerName != null) {
            list.add(new TextComponent(prefix).withStyle(ChatFormatting.WHITE)
                    .append(new TranslatableComponent("tooltips.touhou_little_maid.info.owner")
                            .append(": ").withStyle(ChatFormatting.AQUA))
                    .append(ownerName));
        }
        MaidModelInfo modelInfo = MaidModelRegistry.get(this.maid.getModelId());
        if (modelInfo != null) {
            list.add(new TextComponent(prefix).withStyle(ChatFormatting.WHITE)
                    .append(new TranslatableComponent("tooltips.touhou_little_maid.info.model_name")
                            .append(": ").withStyle(ChatFormatting.AQUA))
                    .append(parseModelName(modelInfo)));
        }
        list.add(new TextComponent(prefix).withStyle(ChatFormatting.WHITE)
                .append(new TranslatableComponent("tooltips.touhou_little_maid.info.experience")
                        .append(": ").withStyle(ChatFormatting.AQUA))
                .append(String.valueOf(this.maid.getExperience())));
        list.add(new TextComponent(prefix).withStyle(ChatFormatting.WHITE)
                .append(new TranslatableComponent("tooltips.touhou_little_maid.info.favorability")
                        .append(": ").withStyle(ChatFormatting.AQUA))
                .append(String.valueOf(this.maid.getFavorability())));

        this.renderComponentTooltip(poseStack, list, mouseX, mouseY);
    }

    /**
     * 取主人显示名。
     *
     * <p>上游直接 {@code maid.getOwner().getDisplayName()}，但客户端上
     * {@code TamableAnimal#getOwner()} 依赖「按 UUID 找到已加载的玩家实体」，
     * 主人离线 / 不在附近时永远为 {@code null}。这里做三级回退：
     * {@code getOwner()} → 本地玩家（UUID 相同）→ UUID 前 8 位。</p>
     */
    @Nullable
    private Component resolveOwnerName() {
        LivingEntity owner = this.maid.getOwner();
        if (owner != null) {
            return owner.getDisplayName();
        }
        UUID ownerUUID = this.maid.getOwnerUUID();
        if (ownerUUID == null) {
            return null;
        }
        LocalPlayer self = Minecraft.getInstance().player;
        if (self != null && ownerUUID.equals(self.getUUID())) {
            return self.getDisplayName();
        }
        return new TextComponent(ownerUUID.toString().substring(0, 8));
    }

    /**
     * 把模型的 {@code name} 字段转成可显示文本，对应上游 {@code ParseI18n.parse}。
     *
     * <p>旧版模型没写 {@code name} 时，{@code MaidModelInfo} 会自动生成
     * {@code {model.ns.path.name}}；包进花括号的一律当翻译键处理（去掉花括号再
     * {@link TranslatableComponent}），否则按字面量显示。</p>
     */
    private static Component parseModelName(MaidModelInfo info) {
        String name = info.getName();
        if (name == null || name.isEmpty()) {
            return new TextComponent(info.getModelId().toString());
        }
        if (name.length() > 2 && name.startsWith("{") && name.endsWith("}")) {
            return new TranslatableComponent(name.substring(1, name.length() - 1));
        }
        return new TextComponent(name);
    }

    private void renderButtonTooltips(PoseStack poseStack, int mouseX, int mouseY) {
        this.renderMaidInfo(poseStack, mouseX, mouseY);
        this.renderStateTooltip(this.homeButton, poseStack, mouseX, mouseY,
                "gui.touhou_little_maid.button.home");
        this.renderStateTooltip(this.pickButton, poseStack, mouseX, mouseY,
                "gui.touhou_little_maid.button.pickup");
        this.renderStateTooltip(this.rideButton, poseStack, mouseX, mouseY,
                "gui.touhou_little_maid.button.maid_riding_set");

        this.renderWidgetTooltip(this.taskSwitchButton, poseStack, mouseX, mouseY,
                "gui.touhou_little_maid.task.switch");
        this.renderWidgetTooltip(this.pageUpButton, poseStack, mouseX, mouseY,
                "gui.touhou_little_maid.task.previous_page");
        this.renderWidgetTooltip(this.pageDownButton, poseStack, mouseX, mouseY,
                "gui.touhou_little_maid.task.next_page");
        this.renderWidgetTooltip(this.pageCloseButton, poseStack, mouseX, mouseY,
                "gui.touhou_little_maid.task.close");

        // 右侧「任务手册」按钮：两行提示（标题 + 说明），与上游侧边栏一致
        if (this.taskBookButton != null && isMouseOver(this.taskBookButton, mouseX, mouseY)) {
            List<Component> tooltip = this.taskBookButton.getTooltip();
            if (tooltip != null) {
                this.renderComponentTooltip(poseStack, tooltip, mouseX, mouseY);
                return;
            }
        }

        // 页签：只给「未选中」的那些弹提示（选中的点了也没用）
        for (MaidTabButton tab : this.tabButtons) {
            if (!tab.active || !isMouseOver(tab, mouseX, mouseY)) {
                continue;
            }
            this.renderComponentTooltip(poseStack, List.of(
                    new TranslatableComponent("gui.touhou_little_maid.button." + tab.getLabelKey()),
                    new TranslatableComponent("gui.touhou_little_maid.button." + tab.getLabelKey() + ".desc")),
                    mouseX, mouseY);
            return;
        }

        // 任务按钮：一次只显示一个，命中即返回
        for (TaskButton button : this.taskButtons) {
            if (!button.visible || !isMouseOver(button, mouseX, mouseY)) {
                continue;
            }
            List<Component> tooltip = button.getTooltip();
            if (tooltip != null) {
                this.renderComponentTooltip(poseStack, tooltip, mouseX, mouseY);
                return;
            }
        }
    }

    /**
     * 开关型按钮（Home / 拾取）的两行提示：状态行 + 说明行。
     */
    private void renderStateTooltip(AbstractWidget button, PoseStack poseStack, int mouseX, int mouseY,
                                    String key) {
        if (button == null || !isMouseOver(button, mouseX, mouseY) || !(button instanceof StateSwitchingButton state)) {
            return;
        }
        List<Component> list = List.of(
                new TranslatableComponent(key + "." + state.isStateTriggered()),
                new TranslatableComponent(key + ".desc"));
        this.renderComponentTooltip(poseStack, list, mouseX, mouseY);
    }

    /**
     * 普通按钮的单行提示。
     */
    private void renderWidgetTooltip(@Nullable AbstractWidget button, PoseStack poseStack, int mouseX, int mouseY,
                                     String key) {
        if (button == null || !isMouseOver(button, mouseX, mouseY)) {
            return;
        }
        this.renderComponentTooltip(poseStack, List.of(new TranslatableComponent(key)), mouseX, mouseY);
    }

    /**
     * 鼠标是否落在 widget 上（同时要求它可见）。
     *
     * <p>{@code AbstractWidget#isMouseOver} 是 protected 且不检查 visible，
     * 这里自己写一份，翻页后隐藏的按钮就不会再弹出提示。</p>
     */
    protected static boolean isMouseOver(AbstractWidget widget, int mouseX, int mouseY) {
        return widget.visible
               && mouseX >= widget.x && mouseX < widget.x + widget.getWidth()
               && mouseY >= widget.y && mouseY < widget.y + widget.getHeight();
    }

    protected static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
