package com.github.tartaricacid.touhoulittlemaid.client.renderer.ai;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图标气泡的「贴图原始尺寸」查询，用来决定一张表情图里有几帧动画。
 *
 * <h2>为什么不学上游的 {@code SizeTexture}</h2>
 * <p>1.18.2 上游自建了一个 {@code SizeTexture extends AbstractTexture}，靠替换
 * 纹理管理器的实现来记住宽高 —— 那需要 mixin 或 AT。本移植版换成更轻的办法：
 * 直接从资源管理器里读 PNG 的文件头（IHDR 里就是宽高，偏移固定），
 * 读到的结果缓存起来。</p>
 *
 * <h2>表情图的排布</h2>
 * <p>台本里 {@code %touhou_little_maid:textures/chat_bubble/emoji/idle1.png%} 这种图是
 * <b>60×20 一帧、竖直堆叠</b>（{@code idle1.png} 是 60×160 → 8 帧）。
 * 于是帧数 = 高度 / 20，播放速度 = 每 2 tick 换一帧（与上游 {@code (tickCount / 2) % count} 一致）。</p>
 */
public final class IconTextureSizes {
    /** 一帧的宽高：60×20（3:1），与上游 {@code drawIcon} 的绘制尺寸一致。 */
    public static final int FRAME_WIDTH = 60;
    public static final int FRAME_HEIGHT = 20;

    /** 读不到时的兜底：当作单帧，静态显示。 */
    private static final int[] UNKNOWN = {FRAME_WIDTH, FRAME_HEIGHT};

    private static final Map<ResourceLocation, int[]> CACHE = new ConcurrentHashMap<>();

    private IconTextureSizes() {
    }

    /**
     * 这张贴图的像素尺寸。
     *
     * @return {@code {width, height}}；读不到时返回 {@code {60, 20}}
     */
    public static int[] get(ResourceLocation location) {
        return CACHE.computeIfAbsent(location, IconTextureSizes::read);
    }

    /**
     * 动画帧数（至少 1）。
     */
    public static int getFrameCount(ResourceLocation location) {
        int[] size = get(location);
        if (size[0] <= 0 || size[1] < FRAME_HEIGHT) {
            return 1;
        }
        return Math.max(1, size[1] / FRAME_HEIGHT);
    }

    public static void clear() {
        CACHE.clear();
    }

    private static int[] read(ResourceLocation location) {
        int[] size = readPngSize(location);
        return size == null ? UNKNOWN : size;
    }

    /**
     * 读 PNG 的 IHDR。
     *
     * <p>PNG 布局固定：8 字节签名 + 4 字节长度 + {@code "IHDR"} + 4 字节宽 + 4 字节高。
     * 只读这 24 个字节，不必解码整张图。</p>
     */
    @Nullable
    private static int[] readPngSize(ResourceLocation location) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getResourceManager() == null) {
            return null;
        }
        try {
            // 1.18.2 的 ResourceManager#getResource 直接返回 Resource（找不到时抛 IOException），
            // 不是 1.19+ 的 Optional —— 所以这里用 try/catch 而不是 isEmpty()
            Resource resource = minecraft.getResourceManager().getResource(location);
            try (InputStream stream = resource.getInputStream()) {
                byte[] header = stream.readNBytes(24);
                if (header.length < 24) {
                    return null;
                }
                // 8 字节签名 89 50 4E 47 0D 0A 1A 0A，之后是 IHDR
                if ((header[0] & 0xFF) != 0x89 || header[1] != 'P' || header[2] != 'N'
                        || header[3] != 'G' || header[12] != 'I' || header[13] != 'H') {
                    return null;
                }
                return new int[]{readInt(header, 16), readInt(header, 20)};
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }
}

