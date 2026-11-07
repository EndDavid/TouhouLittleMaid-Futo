package com.github.tartaricacid.touhoulittlemaid;

import com.github.tartaricacid.touhoulittlemaid.command.MaidCommand;
import com.github.tartaricacid.touhoulittlemaid.entity.backpack.BackpackManager;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitMenu;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import net.fabricmc.api.ModInitializer;

/**
 * Touhou Little Maid: Futo —— Fabric 1.18.2 移植版主入口。
 *
 * <p>初始化顺序有依赖关系，不要随意调整：</p>
 * <ol>
 *     <li>{@code InitSounds} / {@code InitEntities} / {@code InitItems} / {@code InitMenu}：
 *         纯注册，彼此之间只有「实体 → 刷怪蛋」「实体 → 菜单」这类单向依赖；</li>
 *     <li>配置注册：必须早于任何读取配置的代码；</li>
 *     <li>{@code TaskManager} / {@code BackpackManager}：女仆实体的同步数据默认值与
 *         存档读取都会查这两张表，因此必须早于任何女仆被创建；</li>
 *     <li>{@code MaidCommand}：命令注册，顺序无关。</li>
 * </ol>
 */
public final class TouhouLittleMaidFuto implements ModInitializer {
    @Override
    public void onInitialize() {
        InitSounds.init();
        InitEntities.init();
        InitItems.init();
        InitMenu.init();

// 注册配置（Forge Config API Port 提供与 Forge 完全相同的 API）
        net.minecraftforge.api.ModLoadingContext.registerConfig(
                TouhouLittleMaid.MOD_ID,
                net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                com.github.tartaricacid.touhoulittlemaid.config.GeneralConfig.init());
        net.minecraftforge.api.ModLoadingContext.registerConfig(
                TouhouLittleMaid.MOD_ID,
                net.minecraftforge.fml.config.ModConfig.Type.SERVER,
                com.github.tartaricacid.touhoulittlemaid.config.ServerConfig.init());

        // 工作模式注册表（idle / attack …），必须在任何女仆实体创建之前就绪：
        // EntityMaid 的同步数据默认值与存档读取都会查这张表
        TaskManager.init();
        com.github.tartaricacid.touhoulittlemaid.entity.ai.fishing.FishingTypeManager.init();
        // maid meals (work meal / heal meal), see MaidMealManager
        com.github.tartaricacid.touhoulittlemaid.entity.task.meal.MaidMealManager.init();

        // 背包注册表（empty 等），同样必须早于任何女仆创建
        BackpackManager.init();

        // 网络包：GUI 里切换居家 / 拾取时，由服务端写入同步数据
        com.github.tartaricacid.touhoulittlemaid.network.MaidConfigPacket.initServer();

        // 灵力（Power）：死亡扣减、重生复制、脏数据向客户端同步。
        // P 点的唯一常规来源是女仆妖精（见 InitEntities.FAIRY），
        // 之前那个「女仆击杀掉 P 点」的临时入口已删除。
        com.github.tartaricacid.touhoulittlemaid.event.PowerEvents.init();
        // 抱着女仆时拿鞍右击空气 = 放下（上游 CancelSaddleMaidEvent）
        com.github.tartaricacid.touhoulittlemaid.event.MaidSaddleEvents.init();
        // carried maid position: ticked from the vehicle side, see MaidCarryTicker
        com.github.tartaricacid.touhoulittlemaid.event.MaidCarryTicker.initServer();

        // /tlm 管理命令（GUI 之外的批量修改 / 调试入口）
        MaidCommand.init();

        TouhouLittleMaid.LOGGER.info("[Futo] Touhou Little Maid initialized.");
    }
}
