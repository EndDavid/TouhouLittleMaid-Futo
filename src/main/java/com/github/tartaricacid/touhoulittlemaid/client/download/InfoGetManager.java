package com.github.tartaricacid.touhoulittlemaid.client.download;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.download.pojo.DownloadInfo;
import com.github.tartaricacid.touhoulittlemaid.client.download.pojo.DownloadStatus;
import com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.util.ZipFileCheck;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.WorldVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.HttpUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 在线资源包清单的拉取 + 下载安装，移植自上游
 * {@code client/download/InfoGetManager}（Forge 1.18.2 版）。
 *
 * <h2>协议：一份静态 {@code info.json}，不是 REST</h2>
 * <p>整条链路只有两个 GET 请求：</p>
 * <ol>
 *     <li>{@code <root>/info.json} —— 一个 JSON <b>数组</b>，每项是一条资源包记录
 *         （字段见 {@link DownloadInfo}），客户端自己分页 / 分类 / 搜索；</li>
 *     <li>{@code <root>/<url>} —— 包体本身（普通 zip）{@code url} 是相对路径。</li>
 * </ol>
 * <p>两条线路：主站 CDN 与原始源。主站失败自动降级到备用源，并<b>记住这次降级</b>
 * （{@link #USE_BACKUP_URL}），后续包体下载都走同一个源 —— 否则会一半成功一半失败。</p>
 *
 * <h2>本地状态不靠「记住」，靠 CRC32</h2>
 * <p>{@code info.json} 里的 {@code checksum} 是包体 zip 的 CRC32。
 * 界面每次打开都扫一遍 {@code tlm_custom_pack/*.zip} 的实际 CRC32 去比对，
 * 于是「手删文件」「手动放入旧版本」都能被正确识别成未下载 / 需更新。
 * 这个比对在 {@code ModelDownloadGui#checkDownloadInfo} 里做。</p>
 *
 * <h2>与上游的差异</h2>
 * <ul>
 *     <li><b>MD5 用 {@link MessageDigest}、CRC32 用 {@code java.util.zip}。</b>
 *         上游用了 {@code commons-codec} / {@code commons-io}（Forge 自带），
 *         而 Fabric 侧没有保证这两个库一定在，自己算十行代码就够了。</li>
 *     <li><b>版本头换成 {@code X-Fabric-Version}。</b>上游 {@code X-Forge-Version}
 *         取的是 Forge 版本；本移植版取 Fabric Loader 版本，语义一致。</li>
 *     <li><b>不打包内置兜底清单。</b>与上游一致：网络不通且本地没有
 *         {@code config/touhou_little_maid/info.json} 时，界面只显示
 *         「网络好像出了问题」。</li>
 * </ul>
 */
public final class InfoGetManager {
    /**
     * 线路 1：CDN。
     */
    private static final String ROOT_URL = "https://tlmdl.cfpa.team/";
    private static final String INFO_JSON_URL = ROOT_URL + "info.json";
    /**
     * 线路 2：原始源。
     */
    private static final String ROOT_URL_BACKUP = "http://tlm.cfpa.team:29434/";
    private static final String INFO_JSON_URL_BACKUP = ROOT_URL_BACKUP + "info.json";
    /**
     * info 文件最大允许 1M。
     */
    private static final int INFO_MAX_FILE_SIZE = 1024 * 1024;
    /**
     * 包体最大 25M —— 与原版 {@code HttpUtil} 的默认上限一致，也是 Mojang 允许的单文件上限。
     */
    private static final int PACK_MAX_FILE_SIZE = 25 * 1024 * 1024;

    private static final Path ROOT_FOLDER = getRootPath();
    private static final Path INFO_JSON_FILE = ROOT_FOLDER.resolve("info.json");
    private static final Path PACK_FOLDER = ROOT_FOLDER.resolve("file");

    private static volatile boolean USE_BACKUP_URL = false;

    public static volatile List<DownloadInfo> DOWNLOAD_INFO_LIST_ALL = List.of();
    public static volatile List<DownloadInfo> DOWNLOAD_INFO_LIST_MAID = List.of();
    public static volatile List<DownloadInfo> DOWNLOAD_INFO_LIST_CHAIR = List.of();
    public static volatile List<DownloadInfo> DOWNLOAD_INFO_LIST_SOUND = List.of();

    /**
     * 下载按钮角标用的状态：首次（还没成功拉过清单）/ 有更新 / 无更新。
     *
     * <p>点开界面时会把它置成 {@link Statue#NOT_UPDATE}（上游同款），
     * 于是「有新包」的提示只出现一次。</p>
     */
    public static volatile Statue STATUE = Statue.FIRST;

    /**
     * 只用来读 {@code info.json}。{@link DownloadInfo} 里没有 {@link net.minecraft.resources.ResourceLocation}
     * 字段，所以不需要模型加载器那套自定义适配器。
     */
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    private InfoGetManager() {
    }

    /**
     * 请求头。服务端可能用它统计来源，但本移植版不依赖任何一项 ——
     * 少一个字段也不会让下载失败。
     */
    public static Map<String, String> getDownloadHeaders() {
        Map<String, String> map = new HashMap<>();
        Minecraft minecraft = Minecraft.getInstance();
        WorldVersion currentVersion = SharedConstants.getCurrentVersion();

        User user = minecraft.getUser();
        if (user != null) {
            map.put("X-Minecraft-Username", user.getName());
            map.put("X-Minecraft-UUID", user.getUuid());
        }
        map.put("X-Minecraft-Version", currentVersion.getName());
        map.put("X-Minecraft-Version-ID", currentVersion.getId());
        map.put("X-Fabric-Version", modVersion("fabricloader"));
        map.put("X-TLM-Version", modVersion(TouhouLittleMaid.MOD_ID));
        map.put("User-Agent", "Minecraft Java/" + currentVersion.getName());
        return map;
    }

    private static String modVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    /**
     * 启动时拉一次 {@code info.json}（在客户端线程之外异步进行）。
     */
    public static void checkInfoJsonFile() {
        if (!Files.isDirectory(ROOT_FOLDER)) {
            try {
                Files.createDirectories(ROOT_FOLDER);
            } catch (IOException e) {
                TouhouLittleMaid.LOGGER.warn("[Futo] failed to create {}", ROOT_FOLDER, e);
                return;
            }
        }
        try {
            downloadInfoJson(INFO_JSON_FILE.toFile());
        } catch (IOException e) {
            TouhouLittleMaid.LOGGER.warn("[Futo] failed to prepare info.json download", e);
        }
    }

    /**
     * 文件 MD5（十六进制小写）；文件不存在返回空串。
     *
     * <p>只用来判断「这次拉下来的清单和上次是否一样」，决定下载按钮要不要提示更新。</p>
     */
    public static String getFileMd5(File file) {
        if (file == null || !file.isFile()) {
            return "";
        }
        try (FileInputStream stream = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            StringBuilder builder = new StringBuilder();
            for (byte b : digest.digest()) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.warn("[Futo] failed to hash {}", file, e);
            return "";
        }
    }

    /**
     * 清单与下载缓存都放在 {@code <游戏目录>/config/touhou_little_maid/}。
     *
     * <p>放 {@code config} 而不是 {@code tlm_custom_pack}：清单和缓存文件不是资源包，
     * 混进去会被 {@link CustomPackLoader} 当成坏包扫一遍。</p>
     */
    private static Path getRootPath() {
        Path configPath;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gameDirectory != null) {
            URI gameUri = minecraft.gameDirectory.toURI();
            configPath = Paths.get(gameUri).resolve("config");
        } else {
            configPath = Paths.get("./").resolve("config");
        }
        return configPath.resolve(TouhouLittleMaid.MOD_ID);
    }

    /**
     * 异步拉清单：线路 1 失败自动换线路 2，成功则加载并判定「是否有更新」。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void downloadInfoJson(File infoJsonFile) throws IOException {
        Proxy proxy = Minecraft.getInstance().getProxy();
        URL infoJsonUrl = new URL(INFO_JSON_URL);
        URL infoJsonUrlBackup = new URL(INFO_JSON_URL_BACKUP);
        Map<String, String> headers = getDownloadHeaders();

        String md5Previous = getFileMd5(infoJsonFile);

        // 用原始类型而不是 CompletableFuture<?>：HttpUtil.downloadTo 返回 CompletableFuture<?>，
        // 而 exceptionallyCompose 会把通配符捕获成 CAP#1，lambda 返回的另一个 CAP#2 转不过去。
        // 上游同样写的是原始类型（Forge 那个版本靠 @SuppressWarnings 压掉）。
        CompletableFuture downloader = HttpUtil.downloadTo(
                infoJsonFile, infoJsonUrl.toString(), headers, INFO_MAX_FILE_SIZE, null, proxy);
        downloader.exceptionallyCompose(error -> {
            // 线路 1 挂了，换线路 2，并且之后的包体下载都跟着换
            TouhouLittleMaid.LOGGER.warn("[Futo] line 1 is inaccessible, trying line 2");
            USE_BACKUP_URL = true;
            return HttpUtil.downloadTo(infoJsonFile, infoJsonUrlBackup.toString(), headers,
                    INFO_MAX_FILE_SIZE, null, proxy);
        }).thenRun(() -> {
            loadInfoJson(infoJsonFile);
            TouhouLittleMaid.LOGGER.info("[Futo] resource pack list downloaded ({} entries)",
                    DOWNLOAD_INFO_LIST_ALL.size());
            String md5Current = getFileMd5(infoJsonFile);
            if (md5Previous.isEmpty() || md5Current.isEmpty()) {
                return;
            }
            STATUE = md5Previous.equals(md5Current) ? Statue.NOT_UPDATE : Statue.UPDATE;
        }).exceptionally(error -> {
            // 两条线都不通：保留上一次成功拉下来的清单（如果本地有），只记一条日志
            TouhouLittleMaid.LOGGER.warn("[Futo] failed to download info.json, network issue?");
            if (infoJsonFile.isFile()) {
                loadInfoJson(infoJsonFile);
            }
            return null;
        });
    }

    /**
     * 解析本地 {@code info.json}，并切成「全部 / 女仆 / 坐垫 / 声音」四份。
     *
     * <p>用 {@code List.of()} 重新赋值而不是往旧列表里 {@code add}：
     * 界面线程可能正在遍历 —— 整体替换引用是原子的，且 {@code getTypedDownloadInfoList}
     * 读到的是某一轮的完整结果，不会看到「加了一半」。</p>
     */
    private static synchronized void loadInfoJson(File infoJsonFile) {
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(infoJsonFile), StandardCharsets.UTF_8)) {
            List<DownloadInfo> all = GSON.fromJson(reader,
                    new TypeToken<List<DownloadInfo>>() {
                    }.getType());
            if (all == null) {
                all = List.of();
            }
            List<DownloadInfo> maid = new ArrayList<>();
            List<DownloadInfo> chair = new ArrayList<>();
            List<DownloadInfo> sound = new ArrayList<>();
            for (DownloadInfo info : all) {
                info.decorate();
                if (info.hasType(DownloadInfo.TypeEnum.MAID)) {
                    maid.add(info);
                }
                if (info.hasType(DownloadInfo.TypeEnum.CHAIR)) {
                    chair.add(info);
                }
                if (info.hasType(DownloadInfo.TypeEnum.SOUND)) {
                    sound.add(info);
                }
            }
            DOWNLOAD_INFO_LIST_ALL = List.copyOf(all);
            DOWNLOAD_INFO_LIST_MAID = List.copyOf(maid);
            DOWNLOAD_INFO_LIST_CHAIR = List.copyOf(chair);
            DOWNLOAD_INFO_LIST_SOUND = List.copyOf(sound);
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.error("[Futo] failed to parse {}", infoJsonFile, e);
        }
    }

    /**
     * 下载一个资源包（界面里点「下载 / 更新」时调用）。
     *
     * <p>下载到 {@code config/touhou_little_maid/file/} 做缓存，校验 zip 完整后
     * <b>复制</b>进 {@code tlm_custom_pack/}。保留缓存的意义是「删掉已安装的包之后
     * 还能一键装回来」—— 上游也是这个结构。</p>
     */
    public static void downloadPack(DownloadInfo info) {
        try {
            Proxy proxy = Minecraft.getInstance().getProxy();
            info.setStatus(DownloadStatus.DOWNLOADING);
            String rootUrl = USE_BACKUP_URL ? ROOT_URL_BACKUP : ROOT_URL;
            URL url = new URL(new URL(rootUrl), info.getUrl());
            File fileInTlmModel = CustomPackLoader.getPackFolder().resolve(info.getFileName()).toFile();
            File fileInCache = PACK_FOLDER.resolve(info.getFileName()).toFile();

            if (!fileInCache.isFile() || checksumCrc32(fileInCache) != info.getChecksum()) {
                downloadPack(info, fileInCache, url, proxy, fileInTlmModel);
            } else {
                // 缓存就在本地，直接装，不用再走一次网络
                installPack(info, fileInCache, fileInTlmModel);
                sendDownloadMessage(new TranslatableComponent(
                        "gui.touhou_little_maid.resources_download.state.downloaded", info.getFileName(), 0.0));
            }
        } catch (IOException e) {
            info.setStatus(DownloadStatus.NOT_DOWNLOAD);
            TouhouLittleMaid.LOGGER.error("[Futo] failed to start download of {}", info.getFileName(), e);
        }
    }

    private static void downloadPack(DownloadInfo info, File fileInCache, URL url, Proxy proxy, File fileInTlmModel) {
        sendDownloadMessage(new TranslatableComponent(
                "gui.touhou_little_maid.resources_download.state.downloading", info.getFileName()));
        if (!Files.isDirectory(PACK_FOLDER)) {
            try {
                Files.createDirectories(PACK_FOLDER);
            } catch (IOException e) {
                info.setStatus(DownloadStatus.NOT_DOWNLOAD);
                TouhouLittleMaid.LOGGER.error("[Futo] failed to create {}", PACK_FOLDER, e);
                return;
            }
        }
        long startNanos = System.nanoTime();
        HttpUtil.downloadTo(fileInCache, url.toString(), getDownloadHeaders(), PACK_MAX_FILE_SIZE, info, proxy)
                .thenRun(() -> {
                    double seconds = (System.nanoTime() - startNanos) / (double) TimeUnit.SECONDS.toNanos(1);
                    sendDownloadMessage(new TranslatableComponent(
                            "gui.touhou_little_maid.resources_download.state.downloaded",
                            info.getFileName(), seconds));
                    installPack(info, fileInCache, fileInTlmModel);
                })
                .exceptionally(error -> {
                    info.setStatus(DownloadStatus.NOT_DOWNLOAD);
                    TouhouLittleMaid.LOGGER.warn("[Futo] failed to download pack {}, network issue?",
                            info.getFileName());
                    return null;
                });
    }

    /**
     * 校验 → 复制进 {@code tlm_custom_pack} → 重载模型。
     *
     * <p>三个动作的顺序不能换：先验 zip（断线下的半个文件没有 EOCD），
     * 再复制（否则重载时扫到的是一个正在写入的文件），最后重载。</p>
     */
    private static void installPack(DownloadInfo info, File fileInCache, File fileInTlmModel) {
        try {
            if (!ZipFileCheck.isZipFile(fileInCache)) {
                info.setStatus(DownloadStatus.NOT_DOWNLOAD);
                TouhouLittleMaid.LOGGER.error("[Futo] {} is corrupt and cannot be loaded.", info.getFileName());
                return;
            }
            Files.createDirectories(fileInTlmModel.toPath().getParent());
            Files.copy(fileInCache.toPath(), fileInTlmModel.toPath(), StandardCopyOption.REPLACE_EXISTING);
            info.setStatus(DownloadStatus.DOWNLOADED);
            CustomPackLoader.reloadPacks();
        } catch (IOException e) {
            info.setStatus(DownloadStatus.NOT_DOWNLOAD);
            TouhouLittleMaid.LOGGER.error("[Futo] failed to install {}", info.getFileName(), e);
        }
    }

    /**
     * 文件的 CRC32；读不到时返回 {@code -1}（与 {@code checksum} 永远不相等）。
     */
    public static long checksumCrc32(File file) {
        if (file == null || !file.isFile()) {
            return -1L;
        }
        try {
            java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
            try (java.io.InputStream stream = Files.newInputStream(file.toPath())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.read(buffer)) > 0) {
                    crc32.update(buffer, 0, read);
                }
            }
            return crc32.getValue();
        } catch (IOException e) {
            return -1L;
        }
    }

    public static void sendDownloadMessage(MutableComponent component) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null) {
            player.sendMessage(component, Util.NIL_UUID);
        }
    }

    /**
     * 某个页签对应的列表。
     */
    public static List<DownloadInfo> getTypedDownloadInfoList(DownloadInfo.TypeEnum typeEnum) {
        return switch (typeEnum) {
            case CHAIR -> DOWNLOAD_INFO_LIST_CHAIR;
            case SOUND -> DOWNLOAD_INFO_LIST_SOUND;
            default -> DOWNLOAD_INFO_LIST_MAID;
        };
    }

    public static int getPackMaxFileSize() {
        return PACK_MAX_FILE_SIZE;
    }

    public static Path getPackCacheFolder() {
        return PACK_FOLDER;
    }

    /**
     * 下载按钮角标状态。
     */
    public enum Statue {
        /** 还没成功拉过清单 */
        FIRST,
        /** 清单比上次新 */
        UPDATE,
        /** 无更新 */
        NOT_UPDATE
    }
}

