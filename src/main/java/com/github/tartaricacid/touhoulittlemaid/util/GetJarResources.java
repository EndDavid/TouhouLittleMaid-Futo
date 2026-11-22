package com.github.tartaricacid.touhoulittlemaid.util;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
/**
 * 读取模组 jar 自带的资源，移植自上游 {@code util/GetJarResources}。
 *
 * <p>上游靠 {@code TouhouLittleMaid.class.getResource(...)} 拿到 jar 内路径；
 * Fabric 下这一点完全一样（Loader 把模组 jar 与资源包都挂在同一个 classloader 上），
 * 所以实现可以逐字沿用。AI 聊天用它读
 * {@code /assets/touhou_little_maid/config/available_sites.yml}。</p>
 *
 * <p>注意：{@code getResource} 对「目录」在 jar 里不可用，只能取具体文件 ——
 * 需要遍历目录时（角色设定 settings/*.yml）走的是 {@code FabricLoader#findPath}，
 * 见 {@code SettingReader}。</p>
 */
public final class GetJarResources {
    private GetJarResources() {
    }
    /**
     * 复制本模组的文件到指定文件夹。
     *
     * @param filePath jar 里面的文件地址
     * @param destPath 想要复制到的目录
     * @param fileName 复制后的文件名
     */
    public static void copyTouhouLittleMaidFile(String filePath, Path destPath, String fileName) {
        URL url = TouhouLittleMaid.class.getResource(filePath);
        try {
            if (url != null) {
                FileUtils.copyURLToFile(url, destPath.resolve(fileName).toFile());
            }
        } catch (IOException e) {
            TouhouLittleMaid.LOGGER.error("[Futo] failed to copy jar resource {}", filePath, e);
        }
    }
    @Nullable
    public static InputStream readTouhouLittleMaidFile(String filePath) {
        URL url = TouhouLittleMaid.class.getResource(filePath);
        try {
            if (url != null) {
                return url.openStream();
            }
        } catch (IOException e) {
            TouhouLittleMaid.LOGGER.error("[Futo] failed to open jar resource {}", filePath, e);
        }
        return null;
    }
}
