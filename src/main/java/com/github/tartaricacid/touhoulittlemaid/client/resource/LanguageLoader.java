package com.github.tartaricacid.touhoulittlemaid.client.resource;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.download.InfoGetManager;
import com.github.tartaricacid.touhoulittlemaid.client.download.pojo.DownloadInfo;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 「外部语言表」缓存，移植自上游 {@code client/resource/LanguageLoader}。
 *
 * <h2>它解决的是什么问题</h2>
 * <p>模型包的名字/简介<b>不是写死的文案</b>，而是一个键：包里的 {@code maid_model.json}
 * 写 {@code "name": "model.<domain>.<名字>.name"}，在线清单 {@code info.json} 也写
 * {@code "name": "tlm.xxx_pack.name"}，真正的译文放在</p>
 * <ul>
 *     <li>包内 {@code assets/<domain>/lang/<语言代码>.lang}（下载来的模型包）；</li>
 *     <li>{@code info.json} 每条记录的 {@code language} 字段里（在线清单，界面列表用）。</li>
 * </ul>
 * <p>这两处原版都不会读：{@code ResourceManager} 只认资源包里的 {@code .json}
 * 语言文件，而 {@code tlm_custom_pack/} 里的 zip 连资源包都不是。不补这一层，
 * 界面上就全是 {@code tlm.yingbai_arknights_pack.name} 这种原始键。</p>
 *
 * <h2>译文怎么生效</h2>
 * <p>由 {@code mixin/client/LanguageMixin} 在 {@code ClientLanguage#getOrDefault}
 * 的 HEAD 处查这张表。能这么做的前提是 {@code LanguageManager} 会把
 * {@code ClientLanguage} 注入 {@code Language.getInstance()} —— 于是
 * {@code Component.translatable(...)} 的解析全部经过它。</p>
 *
 * <h2>线程</h2>
 * <p>写入方有三个：资源重载线程（{@link com.github.tartaricacid.touhoulittlemaid.client.resource.ModelReloader}）、
 * {@code HttpUtil} 的 IO 线程（{@link InfoGetManager#loadInfoJson} 里清单下载完就登记译文）、
 * 以及渲染线程（下载完成后的重载）。读取方是渲染线程与文字排版，频率极高。
 * 所以缓存必须是 {@link ConcurrentHashMap}，且<b>不许</b>在遍历时清空 ——
 * {@link #clear()} 只在重载入口调用一次。</p>
 *
 * <h2>与上游的差异</h2>
 * <ul>
 *     <li>上游读 {@code .lang} 用 {@code commons-io} 的 {@code IOUtils.readLines}，
 *         Fabric 侧不保证有那个库，这里用 {@link BufferedReader}。</li>
 *     <li>上游的 {@code LANG_CACHE} 是普通 {@code HashMap}，写读跨线程；这里换成并发容器。</li>
 *     <li>上游 {@code getLanguageMap} 只接受 {@code HashMap<String, HashMap<String, String>>}，
 *         这里放宽成 {@code Map<String, ? extends Map<String, String>>}，
 *         清单解析、包内 {@code .lang}、内置表三条路共用一个入口。</li>
 * </ul>
 */
public final class LanguageLoader {
    private static final String COMMENT_SYMBOL = "#";
    /**
     * 上游特意屏蔽的一条字幕：它会在 F3 字幕里刷屏，与模型包无关。
     */
    private static final String REMOVE_KEY = "subtitle.touhou_little_maid.other.credit";
    /**
     * 包内语言文件的路径形态：{@code assets/<域>/lang/zh_cn.lang}。
     *
     * <p>只认两段式语言代码（{@code zh_cn} / {@code en_us} …），与上游同款正则；
     * {@code en_US.lang} 这种大小写混写也能匹配，最后统一转小写。</p>
     */
    private static final Pattern LANG_PATTERN = Pattern.compile("^assets/[\\w.]+/lang/(\\w{2}_\\w{2})\\.lang$");

    /**
     * 语言代码 → （键 → 译文）。语言代码一律小写（{@code zh_cn}）。
     */
    private static final Map<String, Map<String, String>> LANG_CACHE = new ConcurrentHashMap<>();

    private LanguageLoader() {
    }

    /** 丢掉全部外部译文。模型包增删、切换资源包之后必须调一次。 */
    public static void clear() {
        LANG_CACHE.clear();
    }

    /** 取某个语言的译文表；没有就是 {@code null}（不要返回空表，调用方靠 null 判断「没有」）。 */
    @Nullable
    public static Map<String, String> getLanguages(@Nullable String code) {
        return code == null ? null : LANG_CACHE.get(code.toLowerCase(Locale.US));
    }

    /**
     * 把在线清单里每条记录的 {@code language} 字段登记进来。
     *
     * <p>{@link InfoGetManager} 解析完 {@code info.json} 后调一次；
     * 模型包重载时再调一次（因为重载会先 {@link #clear()}）。重复调用是幂等的，
     * 内容相同只是重写一遍。</p>
     */
    public static void loadDownloadInfoLanguages() {
        for (DownloadInfo info : InfoGetManager.DOWNLOAD_INFO_LIST_ALL) {
            HashMap<String, HashMap<String, String>> languages = info.getLanguages();
            if (languages == null) {
                continue;
            }
            merge(languages);
        }
    }

    /**
     * 合并一份「语言代码 → 键值表」。
     *
     * @param languages 允许为 {@code null} 或含空表（远端 JSON 的 {@code language} 字段可以缺省）
     */
    public static void merge(@Nullable Map<String, ? extends Map<String, String>> languages) {
        if (languages == null || languages.isEmpty()) {
            return;
        }
        for (Map.Entry<String, ? extends Map<String, String>> entry : languages.entrySet()) {
            String code = entry.getKey();
            Map<String, String> table = entry.getValue();
            if (code == null || table == null || table.isEmpty()) {
                continue;
            }
            bucket(code).putAll(table);
        }
    }

    /**
     * 读一个来自模型包的语言文件；路径不匹配语言文件时直接返回。
     *
     * @param entryPath 包内完整路径（{@code assets/<域>/lang/zh_cn.lang}），
     *                  <b>不能</b>只传文件名 —— 语言代码就写在目录结构里
     * @param stream    流由调用方关闭
     */
    public static void readLanguageFile(@Nullable String entryPath, @Nullable InputStream stream) {
        if (entryPath == null || stream == null) {
            return;
        }
        Matcher matcher = LANG_PATTERN.matcher(entryPath);
        if (!matcher.matches()) {
            return;
        }
        try {
            readLanguages(bucket(matcher.group(1)), stream);
        } catch (IOException e) {
            TouhouLittleMaid.LOGGER.warn("[Futo] failed to read language file {}", entryPath, e);
        }
    }

    private static Map<String, String> bucket(String code) {
        return LANG_CACHE.computeIfAbsent(code.toLowerCase(Locale.US), key -> new ConcurrentHashMap<>());
    }

    /**
     * 解析 {@code .lang} 的文本格式：{@code 键=值}，一行一条，{@code #} 开头是注释。
     *
     * <p>值里允许再出现 {@code =}（个别字幕带等号），所以只切第一个。</p>
     */
    private static void readLanguages(Map<String, String> target, InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line;
        boolean firstLine = true;
        while ((line = reader.readLine()) != null) {
            if (firstLine) {
                firstLine = false;
                // 带 BOM 的文件会让第一条键多出一个不可见字符，那条译文就永远查不到
                if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                    line = line.substring(1);
                }
            }
            if (line.isEmpty() || line.startsWith(COMMENT_SYMBOL)) {
                continue;
            }
            String[] pair = line.split("=", 2);
            if (pair.length != 2 || REMOVE_KEY.equals(pair[0])) {
                continue;
            }
            target.put(pair[0], pair[1]);
        }
    }
}

