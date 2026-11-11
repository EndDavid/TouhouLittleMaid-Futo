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

        // 灵力 HUD：手持御币时显示 ×N.NN（上游 ShowPowerEvent）
        com.github.tartaricacid.touhoulittlemaid.client.event.ShowPowerEvent.init();
        // 未安装帕秋莉手册时，登录后在聊天栏给出「点这里下载」提示（上游 PlayerLoggedInNotice）
        com.github.tartaricacid.touhoulittlemaid.client.event.PlayerLoggedInNotice.init();
        // 「手持某物指向女仆」的操作提示（上游 client/overlay/MaidTipsOverlay）
        com.github.tartaricacid.touhoulittlemaid.client.overlay.MaidTipsOverlay.init();
        // 指南针：右击女仆后在世界里画她的工作区域（上游 client/event/MaidAreaRenderEvent）
        com.github.tartaricacid.touhoulittlemaid.client.render.MaidAreaRenderer.init();
        // 指南针 / 命名牌的客户端行为（上游 UseNameTagEvent 的客户端那一半 + 区域显示入口）
        com.github.tartaricacid.touhoulittlemaid.client.event.MaidClientInteractEvents.init();
        // carried maid position (client half): see MaidCarryTicker
        com.github.tartaricacid.touhoulittlemaid.event.MaidCarryTicker.initClient();


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
                        GeckoModelLoader.reload();
                    }
                });
    }
}