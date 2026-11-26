package com.github.tartaricacid.touhoulittlemaid.client.download.pojo;

/**
 * 一个资源包的本地状态，移植自上游 {@code client/download/pojo/DownloadStatus}。
 *
 * <p>{@link #NOT_DOWNLOAD} 与 {@link #DOWNLOADED} 不是靠「记住下载过」得到的，
 * 而是每次都拿 {@code info.json} 里的 {@code checksum}（CRC32）与
 * {@code tlm_custom_pack/*.zip} 的实际 CRC32 比对出来的 —— 所以手删文件、
 * 手放文件都能被正确识别。</p>
 */
public enum DownloadStatus {
    /** 未下载 */
    NOT_DOWNLOAD,
    /** 已下载 */
    DOWNLOADED,
    /** 下载中 */
    DOWNLOADING,
    /** 本地是旧版本（CRC32 命中了 {@code old_version} 列表） */
    NEED_UPDATE;

    /**
     * 这个状态下「下载」按钮是否可点。
     */
    public static boolean canDownload(DownloadStatus status) {
        return status == NOT_DOWNLOAD || status == NEED_UPDATE;
    }
}
