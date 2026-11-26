package com.github.tartaricacid.touhoulittlemaid.client;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.MaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityExtinguishingAgentRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.MaidFishingHookRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.AnimationRegister;
import com.github.tartaricacid.touhoulittlemaid.client.resource.GeckoModelLoader;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Touhou Little Maid: Futo —— 客户端入口。
 */
public final class TouhouLittleMaidFutoClient implements ClientModInitializer {
    private static final ResourceLocation MODEL_RELOADER_ID =
            new ResourceLocation("touhou_little_maid", "maid_model_loader");

    @Override
    public void onInitializeClient() {
        // 注册女仆主控制器的动画状态表（坐下 / 走 / 跑 / 待机 / 死亡 …）
        AnimationRegister.registerAnimationState();
        EntityRendererRegistry.register(InitEntities.MAID, MaidRenderer::new);
        net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitMenu.MAID_MAIN,
                com.github.tartaricacid.touhoulittlemaid.client.screen.MaidMainScreen::new);
        // 页签切换是「服务端换菜单」，所以每个页签都要有自己的界面类
        net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitMenu.MAID_CONFIG,
                com.github.tartaricacid.touhoulittlemaid.client.screen.MaidConfigScreen::new);
        net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitMenu.TASK_CONFIG_DEFAULT,
                com.github.tartaricacid.touhoulittlemaid.client.screen.DefaultTaskConfigScreen::new);
        net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitMenu.TASK_CONFIG_ATTACK,
                com.github.tartaricacid.touhoulittlemaid.client.screen.AttackTaskConfigScreen::new);
        // 隙间（无线 I/O）：右键空气打开的 9 格过滤清单界面
        net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitMenu.WIRELESS_IO,
                com.github.tartaricacid.touhoulittlemaid.client.gui.item.WirelessIOContainerGui::new);
        // 野餐篮：右键空气打开的 9 格食物界面（借原版双层箱子底图的上半截）
        net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitMenu.PICNIC_BASKET,
                com.github.tartaricacid.touhoulittlemaid.client.gui.item.PicnicBasketContainerScreen::new);
        // 零食柜（1.20.1 新增）不需要在这里注册界面：
        // 它的菜单是原版 ChestMenu.sixRows，用的是 MenuType.GENERIC_9x3，
        // 而原版的 MenuScreens 早就把这个类型绑到了 ChestScreen —— 重复注册只会多一条无用表项。

        EntityRendererRegistry.register(InitEntities.EXTINGUISHING_AGENT, EntityExtinguishingAgentRenderer::new);
        EntityRendererRegistry.register(InitEntities.MAID_FISHING_HOOK, MaidFishingHookRenderer::new);
        EntityRendererRegistry.register(InitEntities.DANMAKU,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityDanmakuRenderer::new);
        EntityRendererRegistry.register(InitEntities.POWER_POINT,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityPowerPointRenderer::new);
        // 覆盖原版经验球的渲染器：把绿色小球换成东方风格的点数贴片。
        // Fabric 的 EntityRendererRegistry 就是往 EntityRenderers 的 Map 里 put，
        // 后注册的覆盖原版那一项，因此对 EXPERIENCE_ORB 这样写是合法的。
        // 同一个 EntityType 注册两次渲染器只是替换表项，不会报错。
        EntityRendererRegistry.register(net.minecraft.world.entity.EntityType.EXPERIENCE_ORB,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.ReplaceExperienceOrbRenderer::new);
        // 覆盖原版史莱姆的渲染器：换成「灵梦油库里」（上游 ReplaceSlimeModel 开关控制）。
        // 贴图用的是上游 1.20.1 重绘的 textures/bedrock/entity/reimu_yukkuri.png。
        // 模型层必须先烘焙 —— EntityYukkuriSlimeRender 的构造函数里会 bakeLayer。
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
                com.github.tartaricacid.touhoulittlemaid.client.model.EntityYukkuriModel.LAYER,
                com.github.tartaricacid.touhoulittlemaid.client.model.EntityYukkuriModel::createBodyLayer);
        EntityRendererRegistry.register(net.minecraft.world.entity.EntityType.SLIME,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityYukkuriSlimeRender::new);
        // 岩浆怪同理换成「魔理沙油库里」。这一版几何与贴图都是上游 1.20.1 的基岩模型
        // （128x128、14 根骨骼，与 1.18.2 的手写网格不兼容），因此不需要烘焙模型层 ——
        // 几何在首次渲染时由 BedrockEntityModels 从 JSON 读入。
        EntityRendererRegistry.register(net.minecraft.world.entity.EntityType.MAGMA_CUBE,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMarisaYukkuriSlimeRender::new);

        // 原版物品图标替换：附魔之瓶 → 蓝色「点」、不死图腾 → 「命点」
        // （上游 1.20.1 的 InitSpecialItemRender，走 Fabric 的 ModelVariantProvider）
        com.github.tartaricacid.touhoulittlemaid.client.init.InitSpecialItemRender.init();


        // 残局道具的棋盘缩略图 tooltip（上游 InitClientTooltip，Fabric 用 TooltipComponentCallback）
        com.github.tartaricacid.touhoulittlemaid.client.init.InitClientTooltip.init();

        // 女仆妖精：模型层必须先烘焙，渲染器的构造函数里会 bakeLayer。
        // 两套模型都要登记 —— EntityFairyRenderer 的构造函数里同时 new 了
        // NewEntityFairyRenderer，即使配置关着（走旧模型）也会去 bakeLayer 新模型那一层。
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
                com.github.tartaricacid.touhoulittlemaid.client.model.EntityFairyModel.LAYER,
                com.github.tartaricacid.touhoulittlemaid.client.model.EntityFairyModel::createBodyLayer);
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
                com.github.tartaricacid.touhoulittlemaid.client.model.NewEntityFairyModel.LAYER,
                com.github.tartaricacid.touhoulittlemaid.client.model.NewEntityFairyModel::createBodyLayer);
        EntityRendererRegistry.register(InitEntities.FAIRY,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityFairyRenderer::new);

        // 祭坛与神社：两者都是 ENTITYBLOCK_ANIMATED，方块本身不画，全靠方块实体渲染器。
        // 模型层必须先烘焙，渲染器的构造函数里会 bakeLayer。

        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.ALTAR_TE,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityAltarRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.SHRINE_TE,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityShrineRenderer::new);
        // 中国象棋棋盘：也是 ENTITYBLOCK_ANIMATED，棋子与棋盘
        // 全靠基岩模型画（见 TileEntityCChessRenderer）
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.CCHESS_TE,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityCChessRenderer::new);
        // 五子棋 / 国际象棋：同一个套路（ENTITYBLOCK_ANIMATED + 基岩模型），
        // 见各自的渲染器。三块棋盘共用 BoardRenderUtil 里的姿态。
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.GOMOKU_TE,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityGomokuRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.WCHESS_TE,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityWChessRenderer::new);

        // 娱乐方块（键盘 / 书架 / 电脑）：与棋盘同一套路（ENTITYBLOCK_ANIMATED + 方块实体渲染器），
        // 但它们是普通静态模型，需要先把模型层烘焙好（渲染器构造函数里会 bakeLayer）。
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
                com.github.tartaricacid.touhoulittlemaid.client.model.KeyboardModel.LAYER,
                com.github.tartaricacid.touhoulittlemaid.client.model.KeyboardModel::createBodyLayer);
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
                com.github.tartaricacid.touhoulittlemaid.client.model.BookshelfModel.LAYER,
                com.github.tartaricacid.touhoulittlemaid.client.model.BookshelfModel::createBodyLayer);
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
                com.github.tartaricacid.touhoulittlemaid.client.model.ComputerModel.LAYER,
                com.github.tartaricacid.touhoulittlemaid.client.model.ComputerModel::createBodyLayer);
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.KEYBOARD_TE,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityKeyboardRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.BOOKSHELF_TE,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityBookshelfRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.COMPUTER_TE,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityComputerRenderer::new);

        // 野餐篮 / 野餐垫。几何直接用 1.20.1 的 models/bedrock/block/picnic_*.json
        // （运行时由 BedrockBlockModels 读，与祭坛 / 神社 / 三张棋盘同一条管线），
        // 唯一需要烘焙的图层是物品栏里那块扁平图标。
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
                com.github.tartaricacid.touhoulittlemaid.client.model.PicnicBasketIconModel.LAYER,
                com.github.tartaricacid.touhoulittlemaid.client.model.PicnicBasketIconModel::createBodyLayer);
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.PICNIC_MAT_TE,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityPicnicMatRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitItems.PICNIC_BASKET,
                new com.github.tartaricacid.touhoulittlemaid.client.renderer.item.PicnicBasketItemRenderer());

        // 零食柜（1.20.1 新增）。与野餐垫同一条基岩模型管线；
        // 界面复用原版六行箱子 —— 27 格正好是 ChestMenu.sixRows 的容量，
        // 所以 Screen 直接注册原版 ChestScreen，不需要自己写界面。
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.SNACK_CABINET_TE,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntitySnackCabinetRenderer::new);

        // 「生成？？？」占位物品：进度图标用它画「祭坛这条配方做出来的东西」。
        // 与野餐篮同一套 —— 烘焙一块扁平图层 + BuiltinItemRenderer 换贴图。
        // 物品本身不进创造物品栏（除了主栏里那些非物品类配方的副本）。
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
                com.github.tartaricacid.touhoulittlemaid.client.model.EntityPlaceholderIconModel.LAYER,
                com.github.tartaricacid.touhoulittlemaid.client.model.EntityPlaceholderIconModel::createBodyLayer);
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitItems.ENTITY_PLACEHOLDER,
                new com.github.tartaricacid.touhoulittlemaid.client.renderer.item.EntityPlaceholderItemRenderer());


        // 魔理沙的扫帚：和女仆一样是 LivingEntityRenderer + 普通模型层。
        //
        // 【必须在这里注册渲染器】。少了这一行，实体本身仍然能正常生成、正常 tick
        // （服务端 /summon 一切正常），但客户端在 LevelRenderer 里会走到
        //     EntityRenderer<? super T> r = dispatcher.getRenderer(entity);   // → null
        //     r.shouldRender(...)                                              // → NPE
        // 于是「一放置就崩」，且崩溃点在渲染循环里，跟扫帚这个类看起来毫无关系。
        // 下面座位实体那段注释说的就是同一个坑。
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
                com.github.tartaricacid.touhoulittlemaid.client.model.BroomModel.LAYER,
                com.github.tartaricacid.touhoulittlemaid.client.model.BroomModel::createBodyLayer);
        EntityRendererRegistry.register(InitEntities.BROOM,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityBroomRenderer::new);
        // 「你一个人骑扫帚是飞不起来的」提示（上游 BroomTipsOverlay）
        com.github.tartaricacid.touhoulittlemaid.client.overlay.BroomTipsOverlay.init();
        // 下扫帚按键（默认 C）：扫帚上 Shift 是下降，所以下车必须是单独的键。
        // 上游 1.20.1 有它，本移植版最初整块漏掉了，结果骑上去下不来。
        com.github.tartaricacid.touhoulittlemaid.client.input.DismountBroomKey.init();
        // 模型切换器界面：服务端发坐标、客户端找到方块实体后开 Screen。
        // 它是纯 Screen（没有物品槽），所以不走 MenuType 那一套。
        com.github.tartaricacid.touhoulittlemaid.network.OpenSwitcherGuiPacket.initClient();
        // 坐垫换肤界面：服务端发实体 id、客户端找到坐垫后开 Screen。
        // 同样是纯 Screen（没有物品槽），不走 MenuType。
        com.github.tartaricacid.touhoulittlemaid.network.OpenChairGuiPacket.initClient();
        // AI 聊天：站点表同步（S2C）与「开设置界面」（S2C）。
        // 两者都是纯客户端处理，因此在这里注册；服务端那两个 C2S 包在主入口注册。
        com.github.tartaricacid.touhoulittlemaid.network.SyncAiSettingPacket.initClient();
        com.github.tartaricacid.touhoulittlemaid.network.OpenMaidAIDataScreenPacket.initClient();
        com.github.tartaricacid.touhoulittlemaid.network.SyncChatTokensPacket.initClient();
        com.github.tartaricacid.touhoulittlemaid.network.SyncMaidAIHistoryPacket.initClient();
        com.github.tartaricacid.touhoulittlemaid.network.SyncMaidAISettingPacket.initClient();
        com.github.tartaricacid.touhoulittlemaid.network.SyncSiteListPacket.initClient();
        com.github.tartaricacid.touhoulittlemaid.network.TTSAudioToClientPacket.initClient();
        com.github.tartaricacid.touhoulittlemaid.network.TTSSystemAudioToClientPacket.initClient();
        // 对着可聊天的女仆按聊天键（默认 T）开界面
        com.github.tartaricacid.touhoulittlemaid.client.event.PressAIChatKeyEvent.init();
        // 实体 ID 复制器：手持它右键任意实体，把注册名复制到剪贴板（上游 CopyEntityIdEvent）。
        // 必须是客户端：剪贴板与聊天提示都是本地的东西。
        com.github.tartaricacid.touhoulittlemaid.client.event.CopyEntityIdEvent.init();
        // 「打开模组配置界面」按键（默认未绑定）。
        // 上游在 Forge 上靠 ConfigGuiHandler 暴露配置界面，Fabric 上那个入口由 ModMenu 提供，
        // 而 ModMenu 是可选的 —— 没装的玩家就完全没有入口。这个键补上那个缺口。
        com.github.tartaricacid.touhoulittlemaid.client.input.OpenConfigKey.init();
        // 装了 OptiFine 就在第一次进标题画面时弹一页警告（上游 ShowOptifineScreen）。
        // 受 MiscConfig#CloseOptifineWarning 控制 —— 那个开关此前没有任何消费者。
        com.github.tartaricacid.touhoulittlemaid.client.event.ShowOptifineScreen.init();
        // 配置界面自检：只在 JVM 参数 -Dfuto.cloth.selftest=true 时激活。
        // Boolean.getBoolean 为假时右侧整个短路，ClothConfigSelfTest 连加载都不会发生，
        // 所以玩家和「没装 Cloth」的环境都不会碰到它。
        if (Boolean.getBoolean("futo.cloth.selftest")
                && com.github.tartaricacid.touhoulittlemaid.compat.cloth.ClothConfigCompat.isAvailable()) {
            com.github.tartaricacid.touhoulittlemaid.compat.cloth.ClothConfigSelfTest.init();
        }


        // 座位实体不该被看见，但必须有渲染器：未注册类型的
        // getRenderer 返回 null，渲染循环不处处判空（同 P 点实体那个坑）。
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitEntities.SIT,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EmptyEntityRenderer::new);
        // 神社庭灯与稻草人的模型来自 1.20.1，字段里写的是 "render_type": "cutout"。
        // 1.18.2 的 BlockModel 不认这个字段（它是 1.19.4 才进原版的），
        // 所以必须在这里显式把方块登记成 cutout，否则透明区域会渲染成黑色实心。
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.MAID_BEACON,
                net.minecraft.client.renderer.RenderType.cutout());
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.SCARECROW,
                net.minecraft.client.renderer.RenderType.cutout());

        // 墓碑与祭坛召唤箱：模型层同样必须先烘焙（渲染器构造函数里 bakeLayer）。
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
                com.github.tartaricacid.touhoulittlemaid.client.model.TombstoneModel.LAYER,
                com.github.tartaricacid.touhoulittlemaid.client.model.TombstoneModel::createBodyLayer);
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
                com.github.tartaricacid.touhoulittlemaid.client.model.EntityBoxModel.LAYER,
                com.github.tartaricacid.touhoulittlemaid.client.model.EntityBoxModel::createBodyLayer);
        EntityRendererRegistry.register(InitEntities.TOMBSTONE,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityTombstoneRenderer::new);
        EntityRendererRegistry.register(InitEntities.BOX,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityBoxRender::new);

        // 坐垫：实体渲染走 Gecko 管线；物品图标用 Fabric 的 BuiltinItemRenderer 直接画实体
        EntityRendererRegistry.register(InitEntities.CHAIR,
                com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityChairRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(
                com.github.tartaricacid.touhoulittlemaid.init.InitItems.CHAIR,
                new com.github.tartaricacid.touhoulittlemaid.client.renderer.item.ChairItemRenderer());


        // 灵力 HUD：手持御币时显示 ×N.NN（上游 ShowPowerEvent）
        com.github.tartaricacid.touhoulittlemaid.client.event.ShowPowerEvent.init();
        // 未安装帕秋莉手册时，登录后在聊天栏给出「点这里下载」提示（上游 PlayerLoggedInNotice）
        com.github.tartaricacid.touhoulittlemaid.client.event.PlayerLoggedInNotice.init();
        // 「手持某物指向女仆」的操作提示（上游 client/overlay/MaidTipsOverlay）
        com.github.tartaricacid.touhoulittlemaid.client.overlay.MaidTipsOverlay.init();
        // 物品说明：饰品 / 背包追加「需要放置在…」的提示（上游 AddInformationEvent）
        com.github.tartaricacid.touhoulittlemaid.client.event.AddInformationEvent.init();
        // 指南针：右击女仆后在世界里画她的工作区域（上游 client/event/MaidAreaRenderEvent）
        com.github.tartaricacid.touhoulittlemaid.client.render.MaidAreaRenderer.init();
        // 河童的罗盘：手持时预览它自己记下的三个区域（上游 1.20.1 CompassRenderEvent；
        // 1.18.2 没有这个功能，属于按 1.20.1 补齐）
        com.github.tartaricacid.touhoulittlemaid.client.render.CompassAreaRenderer.init();

        // 指南针 / 命名牌的客户端行为（上游 UseNameTagEvent 的客户端那一半 + 区域显示入口）
        com.github.tartaricacid.touhoulittlemaid.client.event.MaidClientInteractEvents.init();
        // 仆从铃：她所在区块没加载时，在世界上画「距离 + 红色倒三角」浮标
        // （上游 client/event/ScrollRenderEvent 的铃那一半；红狐狸卷轴共用这个方法）
        com.github.tartaricacid.touhoulittlemaid.client.render.ServantBellTracker.init();
        // 隙间：手持时把绑定的箱子框成红色线框（上游 WirelessIORenderEvent）
        com.github.tartaricacid.touhoulittlemaid.client.render.WirelessIORenderer.init();

        // 狐狸卷轴记录（S2C）：服务端在物品 use 里打包发来，这里开界面。
        // 记录先读完再 execute —— 出回调后缓冲区就失效了。
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.github.tartaricacid.touhoulittlemaid.network.FoxScrollPacket.CHANNEL,
                (client, handler, buf, responseSender) -> {
                    byte action = buf.readByte();
                    if (action != com.github.tartaricacid.touhoulittlemaid.network.FoxScrollPacket.SHOW_DATA) {
                        return;
                    }
                    java.util.Map<String, java.util.List<com.github.tartaricacid.touhoulittlemaid.network.FoxScrollPacket.FoxScrollData>>
                            data = com.github.tartaricacid.touhoulittlemaid.network.FoxScrollPacket.readData(buf);
                    client.execute(() -> com.github.tartaricacid.touhoulittlemaid.client.screen.FoxScrollScreen
                            .open(data));
                });

        // 灵力同步包（S2C）：客户端只负责写入本地玩家再喂给 HUD
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.github.tartaricacid.touhoulittlemaid.network.PowerSyncPacket.CHANNEL,
                (client, handler, buf, responseSender) -> {
                    float power = buf.readFloat();
                    client.execute(() -> {
                        if (client.player != null) {
                            com.github.tartaricacid.touhoulittlemaid.network.PowerSyncPacket
                                    .applyTo(client.player, power);
                        }
                    });
                });

        // 神社庭灯：服务端通知「打开界面」（S2C）。界面本身是纯 Screen，
        // 不是菜单，所以不走 ScreenRegistry，直接 setScreen。
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.github.tartaricacid.touhoulittlemaid.network.MaidBeaconPacket.CHANNEL,
                (client, handler, buf, responseSender) -> {
                    byte action = buf.readByte();
                    net.minecraft.core.BlockPos pos = buf.readBlockPos();
                    if (action == com.github.tartaricacid.touhoulittlemaid.network.MaidBeaconPacket.OPEN_GUI) {
                        client.execute(() -> com.github.tartaricacid.touhoulittlemaid.client.gui.block.MaidBeaconGui
                                .openIfPresent(client, pos));
                    }
                });

        // 原版指南针：服务端把女仆的三个作息坐标发过来（S2C，上游 SyncMaidAreaMessage）。
        // 数据先读完再 execute —— 出回调后缓冲区就失效了。
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.github.tartaricacid.touhoulittlemaid.network.MaidAreaPacket.CHANNEL,
                (client, handler, buf, responseSender) -> {
                    com.github.tartaricacid.touhoulittlemaid.network.MaidAreaPacket.Area area =
                            com.github.tartaricacid.touhoulittlemaid.network.MaidAreaPacket.read(buf);
                    client.execute(() -> com.github.tartaricacid.touhoulittlemaid.client.render.MaidAreaRenderer
                            .show(area));
                });

        // 象棋：服务端把局面交过来，由本机在后台线程算女仆的下一步
        // （S2C，上游 CChessToClientMessage）。必须先把字段读完再 execute。
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.github.tartaricacid.touhoulittlemaid.network.CChessToClientPacket.CHANNEL,
                (client, handler, buf, responseSender) -> {
                    com.github.tartaricacid.touhoulittlemaid.network.CChessToClientPacket.Payload payload =
                            com.github.tartaricacid.touhoulittlemaid.network.CChessToClientPacket.read(buf);
                    client.execute(() -> com.github.tartaricacid.touhoulittlemaid.client.event.CChessClientHandler
                            .handle(client, payload.getPos(), payload.getFen()));
                });
        // 国际象棋：同样的 S2C，结构也相同（整盘棋一个 FEN 字符串）
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.github.tartaricacid.touhoulittlemaid.network.WChessToClientPacket.CHANNEL,
                (client, handler, buf, responseSender) -> {
                    com.github.tartaricacid.touhoulittlemaid.network.WChessToClientPacket.Payload payload =
                            com.github.tartaricacid.touhoulittlemaid.network.WChessToClientPacket.read(buf);
                    client.execute(() -> com.github.tartaricacid.touhoulittlemaid.client.event.WChessClientHandler
                            .handle(client, payload.getPos(), payload.getFen()));
                });
        // 五子棋：没有 FEN，直接把 15×15 的棋盘发过来（S2C，上游 GomokuToClientMessage）
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.github.tartaricacid.touhoulittlemaid.network.GomokuToClientPacket.CHANNEL,
                (client, handler, buf, responseSender) -> {
                    com.github.tartaricacid.touhoulittlemaid.network.GomokuToClientPacket.Payload payload =
                            com.github.tartaricacid.touhoulittlemaid.network.GomokuToClientPacket.read(buf);
                    client.execute(() -> com.github.tartaricacid.touhoulittlemaid.client.event.GomokuClientHandler
                            .handle(client, payload.getPos(), payload.getChessData(), payload.getPoint(),
                                    payload.getWinCount()));
                });

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return MODEL_RELOADER_ID;
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager resourceManager) {
                        // 方法体抽到 client/resource/ModelReloader：在线下载 / 删除模型包之后
                        // 也要走同一套重载，两处共用一份代码才不会只重载一半。
                        com.github.tartaricacid.touhoulittlemaid.client.resource.ModelReloader.reloadAll();
                    }

                });

        // 在线模型包清单：一次异步 GET，失败就保持「网络好像出了问题」的状态。
        // 放在客户端初始化而不是进世界时，是因为下载按钮的角标要在主菜单之后就正确。
        com.github.tartaricacid.touhoulittlemaid.client.download.InfoGetManager.checkInfoJsonFile();

        // 下载界面自检（仅 -Dfuto.download.selftest=true 时注册，见 build.gradle）。
        // 这个界面不属于任何 MenuType，没有「build 绿了就等于没问题」这条路 ——
        // 它自己画的九宫格气泡、进度条、EditBox、类型图标都只在 render 里才会跑，
        // 所以必须真的把它打开一次。
        if (Boolean.getBoolean("futo.download.selftest")) {
            int[] ticks = {0};
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
                // 等标题画面彻底稳定（资源加载 + 首次布局都完成）再顶上去，否则
                // 会被 Minecraft 随后的 setScreen 覆盖掉，等于没测。
                if (++ticks[0] != 60) {
                    return;
                }
                if (!(client.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                    return;
                }
                com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid.LOGGER.info(
                        "[Futo] download selftest: opening ModelDownloadGui");
                client.setScreen(new com.github.tartaricacid.touhoulittlemaid.client.gui.entity.ModelDownloadGui());
                com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid.LOGGER.info(
                        "[Futo] download selftest: ModelDownloadGui opened with {} entries",
                        com.github.tartaricacid.touhoulittlemaid.client.download.InfoGetManager
                                .DOWNLOAD_INFO_LIST_ALL.size());
            });
        }
    }
}
