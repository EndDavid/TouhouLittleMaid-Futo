package com.github.tartaricacid.touhoulittlemaid.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * ZIP 完整性检查，移植自上游 {@code util/ZipFileCheck}（上游注明来自
 * <a href="https://juejin.cn/post/7220985690795933756">掘金</a>）。
 *
 * <p><b>为什么只读末尾 22 字节就够：</b>ZIP 的目录结束记录（EOCD）固定是 22 字节，
 * 头 4 字节是签名 {@code 0x06054b50}（小端读作 {@code 50 4B 05 06}）。
 * 没有文件注释时它正好位于文件末尾 22 字节处，所以「seek 到 length-22、读 4 字节、
 * 比对签名」即可判断一个文件是不是完整 ZIP —— 断线下载的半个文件不会有 EOCD。</p>
 *
 * <p>上游用同一个判定做两件事：下载完成后校验缓存文件、启动时跳过 {@code tlm_custom_pack}
 * 里损坏的包。本移植版两者都照抄。</p>
 */
public final class ZipFileCheck {
    /** 没有 ZIP 文件注释时，EOCD 相对文件末尾的偏移。 */
    private static final int RAW_END_OFFSET = 22;
    /** 签名占 4 个字节。 */
    private static final int END_OF_DIR_LENGTH = 4;
    /** {@code 0x06054b50} 的小端字节序。 */
    private static final byte[] END_OF_DIR = new byte[]{0x50, 0x4B, 0x05, 0x06};

    private ZipFileCheck() {
    }

    /**
     * 判断文件是否是一个完整的 ZIP。
     *
     * <p>文件不存在、是目录、长度不足，一律返回 {@code false}（不抛异常）——
     * 调用方多在下载回调里，那里再抛一次异常只会让失败信息更难读。</p>
     */
    public static boolean isZipFile(File file) throws IOException {
        if (file == null || !file.isFile()) {
            return false;
        }
        long fileLength = file.length();
        if (fileLength <= RAW_END_OFFSET + END_OF_DIR_LENGTH) {
            return false;
        }
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            // seek 到结束标记所在的位置
            randomAccessFile.seek(fileLength - RAW_END_OFFSET);
            byte[] end = new byte[END_OF_DIR_LENGTH];
            randomAccessFile.readFully(end);
            return isEndOfDir(end);
        }
    }

    /**
     * 是否符合目录结束标记。
     */
    private static boolean isEndOfDir(byte[] src) {
        if (src.length != END_OF_DIR_LENGTH) {
            return false;
        }
        for (int i = 0; i < src.length; i++) {
            if (src[i] != END_OF_DIR[i]) {
                return false;
            }
        }
        return true;
    }
}

