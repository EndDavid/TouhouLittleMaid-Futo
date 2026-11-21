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
        // 原版物品图标替换：附魔之瓶 → 蓝色「点」、不死图腾 → 「命点」
        // （上游 1.20.1 的 InitSpecialItemRender，走 Fabric 的 ModelVariantProvider）
        com.github.tartaricacid.touhoulittlemaid.client.init.InitSpecialItemRender.init();


        // 残局道具的棋盘缩略图 tooltip（上游 InitClientTooltip，Fabric 用 TooltipComponentCallback）
        com.github.tartaricacid.touhoulittlemaid.client.init.InitClientTooltip.init();

        // 女仆妖精：模型层必须先烘焙，渲染器的构造函数里会 bakeLayer
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
                com.github.tartaricacid.touhoulittlemaid.client.model.EntityFairyModel.LAYER,
                com.github.tartaricacid.touhoulittlemaid.client.model.EntityFairyModel::createBodyLayer);
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
                        GeckoModelLoader.reload();                        // 方块基岩模型同样要丢缓存，否则资源包换了模型还在用旧的
                        com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels.clear();
                        // 预热两个方块模型：首次渲染时再加载会卡一帧，而且模型写错
                        // 只会在「方块看不见」这种很难定位的现象里暴露，这里提前解析一遍，
                        // 失败会打 error 日志（BedrockBlockModels#load 里）。
                        com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels.get("altar");
                        com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels.get("shrine");
                        // 三张棋盘也一起预热：它们的贴图与几何比祭坛还大，
                        // 而且「看不见棋子」这种症状最怕拖到运行时才暴露。
                        com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels.get("cchess");
                        com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels.get("cchess_pieces");
                        com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels.get("gomoku");
                        com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels.get("gomoku_piece");
                        com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels.get("wchess");
                        com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels.get("wchess_pieces");
                        // 坐垫模型必须【在女仆模型之后】加载：GeckoLibCache 在上面那行被整体清空过
                        com.github.tartaricacid.touhoulittlemaid.client.resource.ChairModelLoader.reload();
                    }

                });
    }
}
