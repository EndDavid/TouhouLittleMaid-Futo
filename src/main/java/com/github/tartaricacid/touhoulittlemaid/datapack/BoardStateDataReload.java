package com.github.tartaricacid.touhoulittlemaid.datapack;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.datapack.pojo.BoardStateRecord;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * 读取数据包里的残局数据，移植自上游 1.20.1 的
 * {@code datapack/resources/BoardStateDataReloadListener}。
 *
 * <h2>为什么挂在 {@code SERVER_DATA} 上</h2>
 * <p>残局数据只在<b>服务端</b>被用到：战利品生成时要把 {@code data} 写进物品 NBT。
 * 客户端显示 tooltip 时用的是物品 NBT 里已经写好的那份字符串，
 * 不需要再读一次数据包 —— 所以这里不必像女仆模型那样挂客户端资源。</p>
 *
 * <h2>为什么逐个 pack 读、而不是 {@code resourceManager.getResource}</h2>
 * <p>上游就是这么写的，而且这里必须这样：{@code getResource} 只返回<b>最上面那个</b>
 * 数据包里的版本，而残局是「可叠加」的内容 —— 资源包作者应该能只写自己的几条，
 * 与模组自带的那几条一起生效。逐 pack 读取再合并，才能做到这一点。</p>
 *
 * <h2>1.18.2 的差异</h2>
 * <p>{@code PackResources#getResource} 在 1.18.2 直接返回 {@code InputStream}
 * （1.20 改成了 {@code IoSupplier}），并且文件不存在时返回 {@code null} 而不是抛异常，
 * 所以这里用 {@code null} 判空。</p>
 */
public final class BoardStateDataReload implements SimpleSynchronousResourceReloadListener {
    /**
     * 监听器自己的 id。与文件名无关，只要唯一即可。
     */
    private static final ResourceLocation ID =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "board_state_data");

    private static final ResourceLocation CHESS_PATH =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "board_states/chess.json");
    private static final ResourceLocation XIANGQI_PATH =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "board_states/xiangqi.json");
    private static final ResourceLocation GOMOKU_PATH =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "board_states/gomoku.json");

    private static final Gson GSON = new Gson();

    private BoardStateDataReload() {
    }

    public static void init() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new BoardStateDataReload());
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        load(resourceManager);
    }

    /**
     * 读取（或重读）全部残局数据。
     *
     * <p><b>为什么要单独开一个入口：</b>这个方法不只被监听器调用。
     * 实测发现 Fabric 的 {@code LootTableEvents.MODIFY} 比本监听器先跑（日志里
     * 那行 <code>gomoku=0 xiangqi=0 chess=0</code> 就是这个原因），于是战利品调用回调拿到
     * 的是空表、池子根本没加进去。所以战利品那边会在发现表为空时
     * 自己调一次这里，彻底摆脱加载顺序。</p>
     */
    public static void load(ResourceManager resourceManager) {
        // 先清除旧数据（数据包可能已经被移除），再逐 pack 合并
        BoardStateData.clear();
        resourceManager.listPacks().forEach(pack -> {
            readData(pack, CHESS_PATH, BoardStateData::addChessRecords);
            readData(pack, XIANGQI_PATH, BoardStateData::addXiangqiRecords);
            readData(pack, GOMOKU_PATH, BoardStateData::addGomokuRecords);
        });
        TouhouLittleMaid.LOGGER.debug("[Futo] board states loaded: gomoku={} xiangqi={} chess={}",
                BoardStateData.getGomokuRecords().size(),
                BoardStateData.getXiangqiRecords().size(),
                BoardStateData.getChessRecords().size());
    }

    private static void readData(PackResources pack, ResourceLocation path, Consumer<List<BoardStateRecord>> adder) {
        InputStream stream;
        try {
            // 1.18.2 这个方法声明了 IOException（文件缺失 / 压缩包损坏），那也只能当作「这个 pack 没有」
            stream = pack.getResource(PackType.SERVER_DATA, path);
        } catch (Exception e) {
            return;
        }
        if (stream == null) {
            return;
        }
        try (InputStream inputStream = stream;
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            List<BoardStateRecord> records = GSON.fromJson(reader,
                    new TypeToken<List<BoardStateRecord>>() {
                    }.getType());
            if (records != null && !records.isEmpty()) {
                adder.accept(records);
            }
        } catch (Throwable e) {
            // 必须是 <code>Throwable</code>，不能只接 <code>Exception</code>：
            // Gson 2.8.9 在遇到 record / 不可写字段时抛的是
            // <code>AssertionError</code>（它是 <code>Error</code>）。一个写错的数据包
            // 不能把整个服务端启动带走 —— 这个盘里已经真实发生过一次。
            TouhouLittleMaid.LOGGER.error("Failed to load board state data from {}", path, e);
        }
    }
}

