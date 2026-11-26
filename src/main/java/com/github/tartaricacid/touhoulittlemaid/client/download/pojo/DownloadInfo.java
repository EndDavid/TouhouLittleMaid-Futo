package com.github.tartaricacid.touhoulittlemaid.client.download.pojo;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.ProgressListener;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * {@code info.json} 里的一条资源包记录，移植自上游
 * {@code client/download/pojo/DownloadInfo}。
 *
 * <p>字段名（{@code file_name} / {@code file_size} / {@code checksum} …
 * 全是下划线命名）与远端 JSON 逐字对应，<b>不要重命名</b> —— 那是服务端的契约。</p>
 *
 * <p><b>为什么实现 {@link ProgressListener}：</b>原版 {@code HttpUtil#downloadTo}
 * 的最后一个参数除了给 {@code ProgressListener} 之外没有别的进度回传通道。
 * 于是「下载进度」这个 UI 状态直接挂在数据对象上：{@code PackInfoButton}
 * 每帧读 {@link #getDownloadProgress()} 画进度条，下载线程往里写，两者都是同一个实例。</p>
 *
 * <p>{@link #downloadProgress} 用 {@code volatile}：写入方是
 * {@code HttpUtil.DOWNLOAD_EXECUTOR} 的工作线程，读取方是渲染线程，
 * 不加重排序保护的话很可能读到 {@code -1} 一直不动（进度条永远不出现）。</p>
 */
public class DownloadInfo implements ProgressListener {
    private static final String[] UNITS = new String[]{"B", "kB", "MB", "GB", "TB"};
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private volatile DownloadStatus status = DownloadStatus.NOT_DOWNLOAD;

    @SerializedName("language")
    private HashMap<String, HashMap<String, String>> language = new HashMap<>();

    @SerializedName("author")
    private List<String> author = List.of();

    @SerializedName("file_name")
    private String fileName = "";

    @SerializedName("file_size")
    private long fileSize;

    @SerializedName("checksum")
    private long checksum;

    @SerializedName("upload_time")
    private long uploadTime;

    @SerializedName("name")
    private String name = "";

    @SerializedName("version")
    private String version = "";

    @SerializedName("url")
    private String url = "";

    @SerializedName("desc")
    private String desc = "";

    @SerializedName("license")
    private String license = "All Right Reserved";

    @SerializedName("website")
    private String website = "";

    /** 搜索用关键词，{@code decorate()} 里统一转小写。 */
    @SerializedName("keyword")
    private String keyword = "";

    @SerializedName("old_version")
    private List<Long> oldVersion = List.of();

    /**
     * type: maid, chair, sound
     */
    @SerializedName("type")
    private List<String> type = List.of();

    @Expose(deserialize = false)
    private String formatFileSize = "";

    @Expose(deserialize = false)
    private String formatData = "";

    @Expose(deserialize = false)
    private volatile int downloadProgress = -1;

    /**
     * 把字节数转成「12.3 MB」这种可读形式，来自
     * <a href="https://stackoverflow.com/a/5599842">StackOverflow</a>。
     *
     * <p>单位表的刻度是 1024，所以 {@code size} 为 0 时直接返回 "0"
     * （{@code log10(0)} 是负无穷，取整会炸）。</p>
     */
    private static String readableFileSize(long size) {
        if (size <= 0) {
            return "0";
        }
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        // 极端值（> TB）会越界，钳一下更安全
        digitGroups = Mth.clamp(digitGroups, 0, UNITS.length - 1);
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + UNITS[digitGroups];
    }

    public List<String> getAuthor() {
        return author;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getChecksum() {
        return checksum;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getUrl() {
        return url;
    }

    public String getDesc() {
        return desc;
    }

    public HashMap<String, HashMap<String, String>> getLanguages() {
        return language;
    }

    public DownloadStatus getStatus() {
        return status;
    }

    public void setStatus(DownloadStatus status) {
        this.status = status;
    }

    public String getFormatData() {
        return formatData;
    }

    public String getLicense() {
        return license;
    }

    public String getFormatFileSize() {
        return formatFileSize;
    }

    public String getWebsite() {
        return website;
    }

    public String getKeyword() {
        return keyword;
    }

    public List<Long> getOldVersion() {
        return oldVersion;
    }

    public List<String> getType() {
        return type;
    }

    /**
     * 反序列化之后的二次加工：预计算可读大小、上传时间，并把关键词转小写。
     *
     * <p>上游同样在加载时一次性做完，界面里就不再反复格式化。</p>
     */
    public DownloadInfo decorate() {
        this.formatFileSize = readableFileSize(getFileSize());
        this.formatData = DATE_FORMAT.format(new Date(this.uploadTime));
        if (this.keyword != null && !this.keyword.isBlank()) {
            this.keyword = this.keyword.toLowerCase(Locale.US);
        } else {
            this.keyword = "";
        }
        // 搜索是 contains 匹配，name / desc 也顺手并进去，否则「按名字搜不到」很难解释
        if (this.name != null && !this.name.isBlank()) {
            this.keyword = this.keyword + " " + this.name.toLowerCase(Locale.US);
        }
        return this;
    }

    public boolean hasType(TypeEnum typeEnum) {
        return type != null && type.contains(typeEnum.getName());
    }

    /**
     * 这个包声明了几种类型。
     *
     * <p><b>与上游的差异（一处修正）：</b>上游是 {@code type.size()}，
     * 而远端 {@code info.json} 里确实存在重复值 —— 实测 169 条里有 7 条的
     * {@code type} 形如 {@code ["sound","sound","maid"]}。直接取 size 会让
     * {@link com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.PackInfoButton}
     * 画成「三种类型的合集」图标，但那个包其实只有女仆 + 声音两种。
     * 这里去重，图标才与内容相符。</p>
     */
    public int getTypeCount() {
        return type == null ? 0 : new java.util.HashSet<>(type).size();
    }

    public int getDownloadProgress() {
        return downloadProgress;
    }

    @Override
    public void progressStartNoAbort(Component component) {
    }

    @Override
    public void progressStart(Component header) {
    }

    @Override
    public void progressStage(Component stage) {
    }

    @Override
    public void progressStagePercentage(int progress) {
        downloadProgress = progress;
    }

    @Override
    public void stop() {
        downloadProgress = -1;
    }

    /**
     * 资源包类型。JSON 里 {@code type} 是字符串数组，元素就是这里的 {@code name()} 小写。
     */
    public enum TypeEnum {
        /** 女仆模型包 */
        MAID,
        /** 坐垫模型包 */
        CHAIR,
        /** 声音包 */
        SOUND;

        public static TypeEnum getTypeByIndex(int index) {
            return TypeEnum.values()[Mth.clamp(index, 0, TypeEnum.values().length - 1)];
        }

        public String getName() {
            return this.name().toLowerCase(Locale.US);
        }
    }

    /**
     * 只在日志里用：把文件名与状态拼起来，避免整对象 {@code toString} 刷屏。
     */
    @Nullable
    @Override
    public String toString() {
        return this.fileName + "@" + this.status;
    }
}
