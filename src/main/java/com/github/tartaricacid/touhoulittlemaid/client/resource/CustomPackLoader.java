package com.github.tartaricacid.touhoulittlemaid.client.resource;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.texture.PackTexture;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.ChairModelInfo;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.file.AnimationFile;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import com.github.tartaricacid.touhoulittlemaid.util.ZipFileCheck;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 游戏目录下 {@code tlm_custom_pack/} 的模型包加载器，对应上游
 * {@code client/resource/CustomPackLoader} 的**外部包那一半**。
 *
 * <h2>为什么非要有它</h2>
 * <p>本移植版的内置模型包是「构建期展开成普通资源」的，所以一直走
 * {@code ResourceManager}（见 {@link GeckoModelLoader} 的类注释）。但
 * <b>玩家下载 / 手放的模型包是磁盘上的 zip</b>，{@code ResourceManager} 看不见它们 ——
 * 不做这一层的话，「在线下载模型包」这个功能下完等于没下：文件存在，
 * 模型列表里一个都不多。</p>
 *
 * <h2>接法：三个注入点，而不是重写整条加载链</h2>
 * <ol>
 *     <li>{@link GeckoModelLoader#reload()} 末尾调 {@link #loadExternalMaidPacks()}，
 *         {@code ChairModelLoader#reload()} 末尾调 {@link #loadExternalChairPacks()} ——
 *         外部包追加在内置包之后，顺序与上游「默认包排最前、其余按域名字典序」一致；</li>
 *     <li>{@link GeckoModelLoader} 的 {@code open(ResourceLocation)} 加一层兜底：
 *         {@code ResourceManager} 找不到时问 {@link #openExternal(ResourceLocation)}。
 *         几何、动画、图标全部走那一个方法，所以只改一处就让整条管线都能读 zip；</li>
 *     <li>贴图单独注册成动态纹理（{@link PackTexture}）—— 贴图不走
 *         {@code ResourceManager}，而是 {@code TextureManager}。</li>
 * </ol>
 *
 * <h2>优先级：内置资源 > 外部包</h2>
 * <p>{@code open()} 先问 {@code ResourceManager}，再问外部包。后果是「外部包用了与内置包
 * 相同的域名」时，它的 {@code maid_model.json} 会被内置那份盖住 —— 这正是上游
 * 「默认包强行覆盖」的等价行为，日志里会留一条 warn。</p>
 *
 * <h2>线程</h2>
 * <p>{@link #scan()} 与两个 {@code loadExternal*} 都跑在资源重载线程上；
 * {@link #openExternal(ResourceLocation)} 在渲染线程也会被读（惰性兜底构建模型），
 * 所以 {@code SOURCES} 用 {@code volatile} 存整份不可变列表，扫描时整体替换，
 * 读方不加锁也不会看到半个目录。</p>
 */
public final class CustomPackLoader {
    /** 与上游一致：游戏目录下的模型包文件夹名。 */
    private static final String PACK_DIR_NAME = "tlm_custom_pack";
    /** 女仆包索引文件名。 */
    private static final String MAID_JSON = "maid_model.json";
    /** 坐垫包索引文件名。 */
    private static final String CHAIR_JSON = "maid_chair.json";
    /** 只认 {@code assets/<domain>/} 这一层目录（上游 {@code DOMAIN} 同样的正则）。 */
    private static final Pattern DOMAIN = Pattern.compile("^assets/([\\w.]+)/$");

    private static final Path PACK_FOLDER = FabricLoader.getInstance().getGameDir().resolve(PACK_DIR_NAME);

    /**
     * 已扫描到的包来源，顺序即加载顺序（文件名排序，保证每次启动结果一致）。
     */
    private static volatile List<PackSource> sources = List.of();

    /**
     * 已经交给 {@code TextureManager} 的贴图，避免每次重载重复注册同一个位置。
     */
    private static final Set<ResourceLocation> REGISTERED_TEXTURES = new LinkedHashSet<>();

    private CustomPackLoader() {
    }

    /** 模型包目录（界面上的「打开资源包文件夹」按钮用的就是它）。 */
    public static Path getPackFolder() {
        return PACK_FOLDER;
    }

    /**
     * 完整重载：内置包 + 外部包 + 坐垫，等价于上游的 {@code CustomPackLoader#reloadPacks()}。
     *
     * <p>下载完成、删除、更新之后都调它。</p>
     *
     * <p><b>线程：</b>这个方法会被两种调用方用到 —— 界面里的按钮回调（渲染线程）与
     * {@code HttpUtil#downloadTo} 的完成回调（<b>IO 线程</b>）。后者直接跑重载会出事：
     * 几何构建本身无所谓线程，但 {@code TextureManager#register} 是普通 {@code HashMap}、
     * {@code RenderSystem} 的调用也要求渲染线程。所以这里统一保证「在渲染线程执行」，
     * 调用方不必自己判断。</p>
     */
    public static void reloadPacks() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (minecraft.renderOnThread()) {
            ModelReloader.reloadAll();
        } else {
            minecraft.execute(ModelReloader::reloadAll);
        }
    }

    // ------------------------------------------------------------------ 扫描

    /**
     * 重新扫描 {@code tlm_custom_pack/}，结果写进 {@link #sources}。
     *
     * <p>包内可见：{@link ModelReloader#reloadAll()} 在一轮重载开始时调一次，
     * 之后女仆 / 坐垫两边都复用结果。之前各自扫描会让同一个目录被 walk 两次。</p>
     *
     * <p>目录不存在就建一个（上游 {@code initPacks} 同样如此）—— 界面上那个
     * 「打开资源包文件夹」按钮要能打开它。</p>
     */
    static void scan() {
        Path folder = PACK_FOLDER;
        if (!Files.isDirectory(folder)) {
            try {
                Files.createDirectories(folder);
            } catch (IOException e) {
                TouhouLittleMaid.LOGGER.warn("[Futo] failed to create {}", folder, e);
                sources = List.of();
                return;
            }
        }

        File[] files = folder.toFile().listFiles();
        if (files == null) {
            sources = List.of();
            return;
        }
        // 排序：上游按域名字典序排包，而「哪几个域存在」取决于扫描顺序，
        // 所以先让文件顺序稳定下来，否则同一个目录两次启动的页签顺序可能不同。
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        List<PackSource> found = new ArrayList<>();
        for (File file : files) {
            if (file.isDirectory()) {
                // 文件夹形式的包：开发时最常用（改一个 json 重启就生效）
                found.add(new FolderSource(file.toPath()));
                continue;
            }
            String name = file.getName().toLowerCase(Locale.US);
            if (!name.endsWith(".zip")) {
                continue;
            }
            try {
                if (ZipFileCheck.isZipFile(file)) {
                    found.add(new ZipSource(file.toPath()));
                } else {
                    // 断线下载、手动改名成 zip 的非 zip 文件都会走到这里
                    TouhouLittleMaid.LOGGER.error("[Futo] {} is not a valid zip file and will be skipped", file.getName());
                }
            } catch (IOException e) {
                TouhouLittleMaid.LOGGER.error("[Futo] failed to check zip {}", file.getName(), e);
            }
        }
        sources = List.copyOf(found);
        if (!found.isEmpty()) {
            TouhouLittleMaid.LOGGER.info("[Futo] found {} custom model pack(s) in {}", found.size(), folder);
        }
    }

    // ------------------------------------------------------------------ 加载

    /**
     * 加载外部包里的女仆模型。由 {@link GeckoModelLoader#reload()} 在**内置包之后**调用。
     *
     * @return 成功登记的模型数（失败 / 重名被跳过的都不计）
     */
    static int loadExternalMaidPacks() {
        int loaded = 0;
        for (PackSource source : sources) {
            for (String domain : source.domains()) {
                JsonObject root = source.readJson("assets/" + domain + "/" + MAID_JSON);
                if (root == null) {
                    continue;
                }
                loaded += loadMaidPack(source, domain, root);
            }
        }
        return loaded;
    }

    /**
     * 加载外部包里的坐垫模型。由 {@code ChairModelLoader#reload()} 在**内置包之后**调用。
     *
     * @return 成功登记的模型数
     */
    static int loadExternalChairPacks() {
        int loaded = 0;
        for (PackSource source : sources) {
            for (String domain : source.domains()) {
                JsonObject root = source.readJson("assets/" + domain + "/" + CHAIR_JSON);
                if (root == null) {
                    continue;
                }
                loaded += loadChairPack(source, domain, root);
            }
        }
        return loaded;
    }

    /**
     * 一个外部女仆包。
     *
     * <p>与 {@link GeckoModelLoader} 内置包那条路的差别只有两处：
     * ①{@code maid_model.json} 从 zip / 文件夹读；
     * ②每张贴图都要额外注册成动态纹理。其余（几何构建、动画合并、骨骼改名）
     * 完全复用同一套代码 —— 那正是把 {@code open()} 做成兜底的价值。</p>
     */

    private static int loadMaidPack(PackSource source, String domain, JsonObject root) {
        MaidModelInfo[] models;
        try {
            models = GeckoModelLoader.GSON.fromJson(root.get("model_list"), MaidModelInfo[].class);
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.error("[Futo] failed to read model_list of custom pack {}", domain, e);
            return 0;
        }
        if (models == null) {
            return 0;
        }
        List<MaidModelInfo> validModels = new ArrayList<>();
        for (MaidModelInfo info : models) {
            try {
                info.decorate();
            } catch (Exception e) {
                TouhouLittleMaid.LOGGER.error("[Futo] illegal model entry in custom pack {}", domain, e);
                continue;
            }
            if (MaidModelRegistry.contains(info.getModelId())) {
                // 与内置包重名：内置优先（open() 的兜底顺序决定的），这里明确跳过，
                // 免得同一个 id 在模型列表里出现两次
                TouhouLittleMaid.LOGGER.warn("[Futo] model {} in pack {} is shadowed by a built-in pack, skipped",
                        info.getModelId(), domain);
                continue;
            }
            MaidModelRegistry.put(info);

            GeoModel geoModel = GeckoModelLoader.buildGeoModel(info);
            if (geoModel == null) {
                TouhouLittleMaid.LOGGER.warn("[Futo] failed to build geometry of {} (pack {})",
                        info.getModelId(), source.name());
            } else {
                GeckoLibCache.getInstance().putGeoModel(info.getModelId(), geoModel);
            }
            AnimationFile animationFile = GeckoModelLoader.buildAnimations(info);
            if (animationFile != null) {
                GeckoLibCache.getInstance().putAnimationFile(info.getModelId(), animationFile);
            }

            registerTexture(info.getTexture());
            if (info.getExtraTextures() != null) {
                info.getExtraTextures().forEach(CustomPackLoader::registerTexture);
            }
            validModels.add(info);
        }
        if (validModels.isEmpty()) {
            return 0;
        }
        MaidModelRegistry.putPack(GeckoModelLoader.buildPack(domain, root, validModels));
        registerTexture(parseIcon(root));
        TouhouLittleMaid.LOGGER.info("[Futo] loaded {} maid model(s) from custom pack {}", validModels.size(), source.name());
        return validModels.size();
    }

    /**
     * 一个外部坐垫包。逻辑与 {@link #loadMaidPack} 同构。
     *
     * @return 成功登记的模型数
     */
    private static int loadChairPack(PackSource source, String domain, JsonObject root) {
        ChairModelInfo[] models;
        try {
            models = GeckoModelLoader.GSON.fromJson(root.get("model_list"), ChairModelInfo[].class);
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.error("[Futo] failed to read chair model_list of custom pack {}", domain, e);
            return 0;
        }
        if (models == null) {
            return 0;
        }
        List<ChairModelInfo> validModels = new ArrayList<>();
        for (ChairModelInfo info : models) {
            try {
                info.decorate();
            } catch (Exception e) {
                TouhouLittleMaid.LOGGER.error("[Futo] illegal chair entry in custom pack {}", domain, e);
                continue;
            }
            if (ChairModelRegistry.getModelIdSet().contains(info.getModelId())) {
                TouhouLittleMaid.LOGGER.warn("[Futo] chair {} in pack {} is shadowed by a built-in pack, skipped",
                        info.getModelId(), domain);
                continue;
            }
            ChairModelRegistry.putInfo(info);

            GeoModel geoModel = GeckoModelLoader.buildGeoModel(info.getModel());
            if (geoModel != null) {
                GeckoLibCache.getInstance().putGeoModel(info.getModelId(), geoModel);
            } else {
                TouhouLittleMaid.LOGGER.warn("[Futo] failed to build chair geometry of {} (pack {})",
                        info.getModelId(), source.name());
            }
            AnimationFile animationFile = ChairModelLoader.buildAnimations(info);
            if (animationFile != null) {
                GeckoLibCache.getInstance().putAnimationFile(info.getModelId(), animationFile);
            }
            registerTexture(info.getTexture());
            validModels.add(info);
        }
        if (validModels.isEmpty()) {
            return 0;
        }
        ChairModelRegistry.putPack(ChairModelLoader.buildPack(domain, root, validModels));
        registerTexture(parseIcon(root));
        TouhouLittleMaid.LOGGER.info("[Futo] loaded {} chair model(s) from custom pack {}", validModels.size(), source.name());
        return validModels.size();
    }

    @Nullable
    private static ResourceLocation parseIcon(JsonObject root) {
        if (root.has("icon") && root.get("icon").isJsonPrimitive()) {
            return ResourceLocation.tryParse(root.get("icon").getAsString());
        }
        return null;
    }

    // ------------------------------------------------------------------ 资源读取

    /**
     * 在外部包里找资源，供 {@link GeckoModelLoader} 的 {@code open()} 兜底。
     *
     * @return 找不到时返回 {@code null}（调用方的语义就是「资源不存在」）
     */
    @Nullable
    public static InputStream openExternal(ResourceLocation location) {
        if (location == null) {
            return null;
        }
        String path = "assets/" + location.getNamespace() + "/" + location.getPath();
        for (PackSource source : sources) {
            InputStream stream = source.open(path);
            if (stream != null) {
                return stream;
            }
        }
        return null;
    }

    /**
     * 把外部包里的贴图注册成动态纹理。
     *
     * <p>三道闸门，顺序不能换：</p>
     * <ol>
     *     <li>内置资源里有这张图 → 什么都不做。原版 {@code TextureManager} 自己会从
     *         {@code ResourceManager} 加载它，我们抢先注册反而会盖掉内置贴图。</li>
     *     <li>本进程已经注册过 → 跳过（{@link #REGISTERED_TEXTURES}）。</li>
     *     <li>外部包里确实有这张图 → 注册。</li>
     * </ol>
     *
     * <p>注册本身要切到客户端线程：{@code TextureManager#byPath} 是普通 {@code HashMap}，
     * 而这里跑在资源重载线程上；{@code Minecraft#execute} 会把它排到下一个 tick。</p>
     */
    public static void registerTexture(@Nullable ResourceLocation location) {
        if (location == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null) {
            return;
        }
        // ① 内置资源优先
        try {
            minecraft.getResourceManager().getResource(location);
            return;
        } catch (IOException ignored) {
            // 内置没有，继续
        }
        // ② 已经注册过
        synchronized (REGISTERED_TEXTURES) {
            if (!REGISTERED_TEXTURES.add(location)) {
                return;
            }
        }
        // ③ 外部包里有没有
        String path = "assets/" + location.getNamespace() + "/" + location.getPath();
        PackSource owner = null;
        for (PackSource source : sources) {
            if (source.has(path)) {
                owner = source;
                break;
            }
        }
        if (owner == null) {
            TouhouLittleMaid.LOGGER.warn("[Futo] texture {} is missing from every custom pack", location);
            return;
        }
        PackSource finalOwner = owner;
        String description = finalOwner.name() + "/" + location;
        minecraft.execute(() -> minecraft.getTextureManager()
                .register(location, new PackTexture(description, () -> finalOwner.openBytes(path))));
    }

    // ------------------------------------------------------------------ 包来源

    /**
     * 一个模型包来源（zip 或文件夹）。
     *
     * <p>抽象的目的只有一个：让「读 json / 读贴图字节 / 列出域名」与存储方式无关，
     * 这样 zip 与文件夹两条路只有几行差异。</p>
     */
    private abstract static class PackSource {
        abstract String name();

        /** 这个包里包含哪些 {@code assets/<domain>/} 域。 */
        abstract List<String> domains();

        /** 资源是否存在。 */
        abstract boolean has(String path);

        /** 打开资源；不存在返回 {@code null}，调用方负责关闭。 */
        @Nullable
        abstract InputStream open(String path);

        /** 一次读完整个资源；不存在返回 {@code null}。 */
        @Nullable
        abstract byte[] openBytes(String path);

        @Nullable
        JsonObject readJson(String path) {
            try (InputStream stream = this.open(path)) {
                if (stream == null) {
                    return null;
                }
                return GsonHelper.fromJson(GeckoModelLoader.GSON,
                        new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
            } catch (Exception e) {
                TouhouLittleMaid.LOGGER.error("[Futo] failed to read {} from pack {}", path, this.name(), e);
                return null;
            }
        }
    }

    /**
     * {@code tlm_custom_pack} 里的一个 zip。
     *
     * <p>每次读都重新 {@code new ZipFile} 再关掉：{@code ZipFile} 不是线程安全的，
     * 而读取会同时发生在重载线程与渲染线程。一次模型包加载只读几百个小条目，
     * 打开/关闭的开销远小于「共享句柄 + 加锁」的复杂度。</p>
     */
    private static final class ZipSource extends PackSource {
        private final Path file;

        ZipSource(Path file) {
            this.file = file;
        }

        @Override
        String name() {
            return this.file.getFileName().toString();
        }

        @Override
        List<String> domains() {
            Set<String> domains = new LinkedHashSet<>();
            try (ZipFile zipFile = new ZipFile(this.file.toFile())) {
                zipFile.stream().map(ZipEntry::getName).forEach(entryName -> {
                    Matcher matcher = DOMAIN.matcher(entryName);
                    if (matcher.find()) {
                        domains.add(matcher.group(1));
                    }
                });
            } catch (IOException e) {
                TouhouLittleMaid.LOGGER.error("[Futo] failed to list zip {}", this.file, e);
            }
            return List.copyOf(domains);
        }

        @Override
        boolean has(String path) {
            try (ZipFile zipFile = new ZipFile(this.file.toFile())) {
                return zipFile.getEntry(path) != null;
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        @Nullable
        InputStream open(String path) {
            byte[] bytes = this.openBytes(path);
            return bytes == null ? null : new ByteArrayInputStream(bytes);
        }

        @Override
        @Nullable
        byte[] openBytes(String path) {
            try (ZipFile zipFile = new ZipFile(this.file.toFile())) {
                ZipEntry entry = zipFile.getEntry(path);
                if (entry == null) {
                    return null;
                }
                try (InputStream stream = zipFile.getInputStream(entry)) {
                    return stream.readAllBytes();
                }
            } catch (IOException e) {
                TouhouLittleMaid.LOGGER.error("[Futo] failed to read {} from {}", path, this.file, e);
                return null;
            }
        }
    }

    /**
     * {@code tlm_custom_pack} 里的一个文件夹（展开态模型包）。
     */
    private static final class FolderSource extends PackSource {
        private final Path root;

        FolderSource(Path root) {
            this.root = root;
        }

        @Override
        String name() {
            return this.root.getFileName().toString();
        }

        @Override
        List<String> domains() {
            Path assets = this.root.resolve("assets");
            if (!Files.isDirectory(assets)) {
                return List.of();
            }
            List<String> domains = new ArrayList<>();
            try (Stream<Path> stream = Files.list(assets)) {
                stream.filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString())
                        .sorted()
                        .forEach(domains::add);
            } catch (IOException e) {
                TouhouLittleMaid.LOGGER.error("[Futo] failed to list {}", assets, e);
            }
            return domains;
        }

        @Override
        boolean has(String path) {
            return Files.isRegularFile(this.root.resolve(path));
        }

        @Override
        @Nullable
        InputStream open(String path) {
            Path file = this.root.resolve(path);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            try {
                return Files.newInputStream(file);
            } catch (IOException e) {
                TouhouLittleMaid.LOGGER.error("[Futo] failed to open {}", file, e);
                return null;
            }
        }

        @Override
        @Nullable
        byte[] openBytes(String path) {
            Path file = this.root.resolve(path);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            try {
                return Files.readAllBytes(file);
            } catch (IOException e) {
                TouhouLittleMaid.LOGGER.error("[Futo] failed to read {}", file, e);
                return null;
            }
        }
    }

    /**
     * 打开系统文件管理器（界面上的「打开资源包文件夹」按钮）。
     *
     * <p>{@link Util#getPlatform()} 在 IO 线程上跑外部命令，所以从渲染线程调它是安全的。</p>
     */
    public static void openPackFolder() {
        File folder = PACK_FOLDER.toFile();
        if (!folder.isDirectory()) {
            try {
                Files.createDirectories(PACK_FOLDER);
            } catch (IOException e) {
                TouhouLittleMaid.LOGGER.warn("[Futo] failed to create {}", PACK_FOLDER, e);
                return;
            }
        }
        Util.getPlatform().openFile(folder);
    }
}

