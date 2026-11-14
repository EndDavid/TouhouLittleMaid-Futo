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
        // 「这次开不了居家模式」的说明（S2C，上游 CheckSchedulePosMessage）。
        // 只在女仆界面开着的时候弹 —— 否则玩家根本不知道自己点过那个开关。
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.github.tartaricacid.touhoulittlemaid.network.CheckSchedulePosPacket.CHANNEL,
                (client, handler, buf, responseSender) -> {
                    net.minecraft.network.chat.Component tips = buf.readComponent();
                    client.execute(() -> {
                        if (client.screen instanceof com.github.tartaricacid.touhoulittlemaid.client.screen
                                .AbstractMaidScreen<?> parent) {
                            client.setScreen(new com.github.tartaricacid.touhoulittlemaid.client.screen
                                    .CheckSchedulePosScreen(parent, tips));
                        }
                    });
                });

        // 女仆界面左侧的状态效果列表（S2C 回传，上游 SendEffectMessage）。
        // 效果不是同步数据 —— 原版只把玩家自己的效果下发给 LocalPlayer ——
        // 所以界面每 20 tick 问一次，服务端把那一刻的完整快照发回来。
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.github.tartaricacid.touhoulittlemaid.network.MaidEffectPacket.CHANNEL,
                (client, handler, buf, responseSender) -> {
                    byte action = buf.readByte();
                    int entityId = buf.readInt();
                    if (action != com.github.tartaricacid.touhoulittlemaid.network.MaidEffectPacket.SYNC) {
                        return;
                    }
                    int size = buf.readInt();
                    // 条数是网络来的，先挡住异常值再分配集合，否则伪造的「20 亿条」会直接 OOM
                    if (size < 0 || size > com.github.tartaricacid.touhoulittlemaid.network.MaidEffectPacket.MAX_EFFECTS) {
                        return;
                    }
                    java.util.List<com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidEffectData> effects =
                            new java.util.ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        effects.add(com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidEffectData.fromBuf(buf));
                    }
                    client.execute(() -> {
                        if (client.level == null) {
                            return;
                        }
                        if (client.level.getEntity(entityId)
                                instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid
                                && maid.isAlive()) {
                            maid.setEffects(effects);
                        }
                    });
                });

        // 女仆粒子包（S2C）：饰品触发时让附近客户端播粒子。
        // 粒子方法本身在 EntityMaid 上，是客户端专用的（上游同款），所以必须走包。
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.github.tartaricacid.touhoulittlemaid.network.MaidParticlePacket.CHANNEL,
                (client, handler, buf, responseSender) -> {
                    int entityId = buf.readInt();
                    int type = buf.readByte();
                    client.execute(() -> {
                        if (client.level != null
                                && client.level.getEntity(entityId)
                                instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid
                                && maid.isAlive()) {
                            com.github.tartaricacid.touhoulittlemaid.network.MaidParticlePacket.handle(maid, type);
                        }
                    });
                });

        // 装备碎裂包（S2C）：LivingEntity#breakItem 是客户端方法，服务端只能通知。
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.github.tartaricacid.touhoulittlemaid.network.ItemBreakPacket.CHANNEL,
                (client, handler, buf, responseSender) -> {
                    int entityId = buf.readInt();
                    net.minecraft.world.item.ItemStack stack = buf.readItem();
                    client.execute(() -> {
                        if (client.level != null
                                && client.level.getEntity(entityId)
                                instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
                            com.github.tartaricacid.touhoulittlemaid.network.ItemBreakPacket.handle(maid, stack);
                        }
                    });
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
                        // 坐垫模型必须【在女仆模型之后】加载：GeckoLibCache 在上面那行被整体清空过
                        com.github.tartaricacid.touhoulittlemaid.client.resource.ChairModelLoader.reload();
                    }

                });
    }
}
