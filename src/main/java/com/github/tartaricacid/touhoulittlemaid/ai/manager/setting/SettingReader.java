package com.github.tartaricacid.touhoulittlemaid.ai.manager.setting;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.google.common.collect.Maps;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
/**
 * 角色设定的注册表，移植自上游 {@code ai/manager/setting/SettingReader}。
 *
 * <p>键是 <b>模型 id</b>（{@code touhou_little_maid:hakurei_reimu} 这种），
 * 值是那份 yml 解析出来的 {@link CharacterSetting}。没有对应条目的模型就没法聊天
 * （会提示 {@code ai.touhou_little_maid.chat.no_setting}）—— 与上游一致。</p>
 *
 * <h2>与上游的差异（来源不同，扫描范围相同）</h2>
 * <p>上游扫两处：{@code config/touhou_little_maid/settings}（最多 8 层）与
 * 模型包（{@code tlm_custom_pack/*.zip} 里的 {@code assets/<domain>/settings/*.yml}）。
 * 但本移植版的内置模型包是 <b>模组 jar 里的资源包 zip</b>，不在游戏目录里，
 * 于是「模型包那一半」改成直接扫 jar 的 {@code assets/<domain>/settings/}：
 * 开发环境与生产环境都能扫到，与 {@code ServerModelPool} 取模型 id 的做法一致。</p>
 * <p>额外的第三处：{@code <游戏目录>/tlm_custom_pack/} 下的文件夹与 zip 仍然会读，
 * 这样玩家自己放进去的模型包也能带设定文件。</p>
 */
public final class SettingReader {
    private static final String SETTING_FOLDER_NAME = "settings";
    private static final Path SETTINGS_FOLDER = Paths.get("config", TouhouLittleMaid.MOD_ID, SETTING_FOLDER_NAME);
    private static final Path CUSTOM_PACK_FOLDER = Paths.get("tlm_custom_pack");
    private static final String YAML_SUFFIX = ".yml";
    private static final java.util.Map<String, CharacterSetting> SETTINGS = Maps.newHashMap();
    public static void clear() {
        SETTINGS.clear();
    }
    /**
     * 重新加载全部角色设定。
     *
     * <p>先去重（{@link #clear()}），再依次读三处；同一模型 id 后读到的覆盖先读到的
     * —— 于是优先级是「玩家配置目录 &gt; 玩家模型包 &gt; jar 内置」，与上游的直觉一致。</p>
     */
    public static void reloadSettings() {
        clear();
        if (!Files.exists(SETTINGS_FOLDER)) {
            try {
                Files.createDirectories(SETTINGS_FOLDER);
            } catch (IOException e) {
                TouhouLittleMaid.LOGGER.error("[Futo] failed to create AI settings folder", e);
            }
        }
        // 配置文件里可以读 8 层
        try {
            readConfigSetting(SETTINGS_FOLDER, 8);
        } catch (IOException e) {
            TouhouLittleMaid.LOGGER.error("[Futo] failed to read AI settings", e);
        }
        readBuiltinSettings();
        readCustomPacks();
        TouhouLittleMaid.LOGGER.info("[Futo] loaded {} character settings for AI chat", SETTINGS.size());
    }
    /**
     * 读模组 jar（开发环境是 build 输出目录）里 {@code assets/<domain>/settings/*.yml}。
     */
    private static void readBuiltinSettings() {
        ModContainer mod = FabricLoader.getInstance().getModContainer(TouhouLittleMaid.MOD_ID).orElse(null);
        if (mod == null) {
            return;
        }
        Path assets = mod.findPath("assets").orElse(null);
        if (assets == null || !Files.isDirectory(assets)) {
            return;
        }
        try (java.util.stream.Stream<Path> domains = Files.list(assets)) {
            domains.filter(Files::isDirectory).forEach(domain -> {
                Path folder = domain.resolve(SETTING_FOLDER_NAME);
                if (Files.isDirectory(folder)) {
                    try {
                        // 模型包里只能读一层
                        readConfigSetting(folder, 1);
                    } catch (IOException e) {
                        TouhouLittleMaid.LOGGER.error("[Futo] failed to read AI settings from " + folder, e);
                    }
                }
            });
        } catch (IOException e) {
            TouhouLittleMaid.LOGGER.warn("[Futo] failed to scan built-in AI settings", e);
        }
    }
    /**
     * 读游戏目录下 {@code tlm_custom_pack/} 里的自定义模型包（文件夹或 zip）。
     */
    private static void readCustomPacks() {
        Path folder = FabricLoader.getInstance().getGameDir().resolve(CUSTOM_PACK_FOLDER);
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (java.util.stream.Stream<Path> entries = Files.list(folder)) {
            entries.forEach(entry -> {
                if (Files.isDirectory(entry)) {
                    readCustomPack(entry, TouhouLittleMaid.MOD_ID);
                } else if (entry.getFileName().toString().endsWith(".zip")) {
                    try (ZipFile zipFile = new ZipFile(entry.toFile())) {
                        readCustomPack(zipFile, TouhouLittleMaid.MOD_ID);
                    } catch (IOException e) {
                        TouhouLittleMaid.LOGGER.error("[Futo] failed to read AI settings from " + entry, e);
                    }
                }
            });
        } catch (IOException e) {
            TouhouLittleMaid.LOGGER.warn("[Futo] failed to scan tlm_custom_pack for AI settings", e);
        }
    }
    public static void readCustomPack(Path rootPath, String domain) {
        Path folder = rootPath.resolve("assets").resolve(domain).resolve(SETTING_FOLDER_NAME);
        // 用 Files.isDirectory 而不是 folder.toFile().isDirectory()：当 rootPath 位于
        // 一个 jar 内（zipfs）时，Path#toFile 会抛 UnsupportedOperationException。
        if (!Files.isDirectory(folder)) {
            return;
        }
        try {
            // 模型包里只能读一层
            readConfigSetting(folder, 1);
        } catch (IOException e) {
            TouhouLittleMaid.LOGGER.error("[Futo] failed to read settings from " + folder, e);
        }
    }
    public static void readCustomPack(ZipFile zipFile, String domain) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        String folder = String.format("assets/%s/%s/", domain, SETTING_FOLDER_NAME);
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();
            if (!entry.isDirectory() && entryName.startsWith(folder) && entryName.endsWith(YAML_SUFFIX)) {
                TouhouLittleMaid.LOGGER.debug("[Futo] loading AI setting from {}", entryName);
                try (InputStream inputStream = zipFile.getInputStream(entry)) {
                    CharacterSetting setting = new CharacterSetting(inputStream);
                    setting.getModelId().forEach(id -> SETTINGS.put(id, setting));
                } catch (IOException e) {
                    TouhouLittleMaid.LOGGER.error("[Futo] failed to read settings from {}", entryName, e);
                }
            }
        }
    }
    private static void readConfigSetting(Path settingFolder, int maxDepth) throws IOException {
        Files.walkFileTree(settingFolder, EnumSet.noneOf(FileVisitOption.class), maxDepth, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(@NotNull Path file, BasicFileAttributes attributes) throws IOException {
                String fileName = file.getFileName().toString();
                if (fileName.endsWith(YAML_SUFFIX)) {
                    // 必须走 InputStream，不能 file.toFile()：
                    // 生产环境的设置文件在 *模组 jar 内部*（readBuiltinSettings 扫的是
                    // ModContainer#findPath("assets")），那里的 Path 是 jdk.zipfs 的 ZipPath，
                    // 对它调 toFile() 会直接抛 UnsupportedOperationException —— 表现为
                    // 「开发环境能启动、把 jar 丢进正式实例就崩在 entrypoint」。
                    try (InputStream stream = Files.newInputStream(file)) {
                        CharacterSetting setting = new CharacterSetting(stream);
                        setting.getModelId().forEach(id -> SETTINGS.put(id, setting));
                    }
                }
                return super.visitFile(file, attributes);
            }
        });
    }
    /**
     * 角色设定目录（{@code config/touhou_little_maid/settings}），供自动生成时落盘用。
     */
    public static Path getSettingsFolder() {
        return SETTINGS_FOLDER;
    }
    /**
     * 把自动生成的设定写成 {@code <模型 id>.yml} 并重新加载。
     *
     * <p><b>已存在同名文件时直接返回 {@code false}，绝不覆盖</b>：玩家手写的设定永远优先于
     * 模型生成的，覆盖一次就是永久丢失。</p>
     *
     * <p>写出的结构与手写文件完全一致（{@code meta.author} / {@code meta.model_id} /
     * {@code setting: |}），因此生成物可以被人直接编辑，也可以被 {@link #reloadSettings()}
     * 当普通文件读回来。</p>
     *
     * @return 是否真的写出了新文件
     */
    public static boolean saveGeneratedSetting(String modelId, String settingText) {
        if (StringUtils.isBlank(modelId) || StringUtils.isBlank(settingText)) {
            return false;
        }
        String fileName = modelId.replace(':', '_').replace('/', '_') + YAML_SUFFIX;
        Path target = SETTINGS_FOLDER.resolve(fileName);
        if (Files.exists(target)) {
            TouhouLittleMaid.LOGGER.info("[Futo] AI setting for {} already exists, not overwriting", modelId);
            return false;
        }
        try {
            Files.createDirectories(SETTINGS_FOLDER);
            StringBuilder builder = new StringBuilder();
            builder.append("meta:\n");
            builder.append("  author: touhou_little_maid\n");
            builder.append("  model_id: [").append(modelId).append("]\n");
            builder.append("setting: |\n");
            for (String line : settingText.split("\n")) {
                builder.append("  ").append(line).append('\n');
            }
            Files.write(target, builder.toString().getBytes(StandardCharsets.UTF_8));
            reloadSettings();
            TouhouLittleMaid.LOGGER.info("[Futo] auto generated AI setting saved to {}", target);
            return true;
        } catch (IOException e) {
            TouhouLittleMaid.LOGGER.error("[Futo] failed to save auto generated AI setting", e);
            return false;
        }
    }
    public static Optional<CharacterSetting> getSetting(@NotNull String name) {
        return Optional.ofNullable(SETTINGS.get(name));
    }
    public static Set<String> getAllSettingKeys() {
        return SETTINGS.keySet();
    }
    private SettingReader() {
    }
}