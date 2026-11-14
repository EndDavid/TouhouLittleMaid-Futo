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
        // 方块 / 方块实体必须在物品之前：ItemMaidBeacon 的构造器要读 InitBlocks.MAID_BEACON
        com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.init();
        // 兴趣点必须晚于方块注册（状态集合是从已注册方块枚举出来的）
        com.github.tartaricacid.touhoulittlemaid.init.InitPoi.init();
        InitItems.init();
        // 饰品登记表：必须晚于物品注册（它按物品实例建立「物品 → 行为」映射），
        // 但必须早于任何女仆被创建 —— BaubleItemHandler 在读档 / 换物时会查它。
        com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager.init();
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

        // 判据触发器必须在任何数据包（进度 JSON）被解析之前注册，
        // 否则 grant_book_on_first_join 里的自定义触发器会被当成「未知判据」而整条进度不加载。
        com.github.tartaricacid.touhoulittlemaid.init.InitTrigger.init();

        // 工作模式注册表（idle / attack …），必须在任何女仆实体创建之前就绪：
        // EntityMaid 的同步数据默认值与存档读取都会查这张表
        TaskManager.init();
        com.github.tartaricacid.touhoulittlemaid.entity.ai.fishing.FishingTypeManager.init();
        // maid meals (work meal / heal meal), see MaidMealManager
        com.github.tartaricacid.touhoulittlemaid.entity.task.meal.MaidMealManager.init();

        // 背包注册表（empty 等），同样必须早于任何女仆创建
        BackpackManager.init();

        // 帕秋莉手册兼容：注册祭坛多方块 + 客户端「点侧边栏开书」监听。
        // 未安装 Patchouli 时本方法内部直接返回，不会加载任何 vazkii.patchouli 类型。
        com.github.tartaricacid.touhoulittlemaid.compat.patchouli.PatchouliCompat.init();

        // 网络包：GUI 里切换居家 / 拾取时，由服务端写入同步数据
        com.github.tartaricacid.touhoulittlemaid.network.MaidConfigPacket.initServer();
        // 网络包：神社庭灯（选效果 / 溢出开关 / 存取的 C2S，打开界面的 S2C）
        com.github.tartaricacid.touhoulittlemaid.network.MaidBeaconPacket.initServer();
        // 网络包：女仆界面左侧的状态效果列表（客户端每 20 tick 拉一次，服务端现查现回）
        com.github.tartaricacid.touhoulittlemaid.network.MaidEffectPacket.initServer();
        // 网络包：仆从铃绑定女仆（C2S，把女仆 UUID + 提示名写进主手那枚铃的 NBT）
        com.github.tartaricacid.touhoulittlemaid.network.ServantBellSetPacket.initServer();

        // 灵力（Power）：死亡扣减、重生复制、脏数据向客户端同步。
        // P 点的唯一常规来源是女仆妖精（见 InitEntities.FAIRY），
        // 之前那个「女仆击杀掉 P 点」的临时入口已删除。
        com.github.tartaricacid.touhoulittlemaid.event.PowerEvents.init();
        // 抱着的女仆时拿鞍右击空气 = 放下（上游 CancelSaddleMaidEvent）
        com.github.tartaricacid.touhoulittlemaid.event.MaidSaddleEvents.init();
        // carried maid position: ticked from the vehicle side, see MaidCarryTicker
        com.github.tartaricacid.touhoulittlemaid.event.MaidCarryTicker.initServer();
        // 玩家登录时主动敲一次「首次登录送手册」判据（对应上游 EnterServerEvent）
        com.github.tartaricacid.touhoulittlemaid.event.EnterServerEvent.init();
        // 神社 / P 点塞进原版箱子战利品（上游的两条全局战利品修饰器）
        com.github.tartaricacid.touhoulittlemaid.event.ChestLootEvent.init();

        // 合成表：帕秋莉手册的《记忆中的幻想乡》（touhou_little_maid:memorizable_gensokyo）
        com.github.tartaricacid.touhoulittlemaid.init.InitRecipes.init();

        // /tlm 管理命令（GUI 之外的批量修改 / 调试入口）
        MaidCommand.init();

        TouhouLittleMaid.LOGGER.info("[Futo] Touhou Little Maid initialized.");
    }
}