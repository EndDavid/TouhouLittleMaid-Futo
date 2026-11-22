package com.github.tartaricacid.touhoulittlemaid.util;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 网络缓冲区的补充读写工具，移植自上游 {@code util/ByteBufUtils} 里 AI 聊天用到的那几个方法。
 *
 * <p>上游把 {@code writeStringSet} / {@code writeSites} 也放在同名类里；本移植版只需要
 * 这两组，因此单独抽出这个小类，不去动已有的其它 util。</p>
 *
 * <h2>为什么不用 {@code buf.writeCollection}</h2>
 * <p>1.18.2 的 {@link FriendlyByteBuf} 有 {@code writeCollection}，但它的函数式参数形式
 * 在不同映射下签名不稳定，而且嵌套泛型（{@code Map<String, List<String>>}）会让调用点
 * 变得难读。手写循环反而更直观，也便于逐字对照上游的编码顺序 ——
 * 这个顺序一旦错位，客户端解出来的是另一份数据而不是报错。</p>
 *
 * <p><b>顺序写死的约定：</b>写入与读取的顺序必须完全一致；任何一方改了顺序，
 * 另一方的界面就会显示成别人的站点名。这是网络协议，不是本地数据。</p>
 */
public final class ByteBufUtils {
    private ByteBufUtils() {
    }

    /**
     * 字符串集合：先写长度，再逐个写（顺序依赖 {@link LinkedHashSet} 的插入顺序，
     * 这样客户端的下拉框顺序与服务端一致）。
     */
    public static void writeStringSet(FriendlyByteBuf buf, Set<String> set) {
        buf.writeVarInt(set.size());
        for (String value : set) {
            buf.writeUtf(value);
        }
    }

    public static Set<String> readStringSet(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        // 上限防御：任何来自网络的长度都要设上限，否则一个畸形包就能让客户端分配巨大数组
        int safeSize = Math.min(Math.max(size, 0), 4096);
        Set<String> set = new LinkedHashSet<>(Math.max(4, safeSize));
        for (int i = 0; i < safeSize; i++) {
            set.add(buf.readUtf());
        }
        return set;
    }

    /**
     * 站点表：{@code 站点名 → 模型列表}，不含密钥。
     */
    public static void writeSites(FriendlyByteBuf buf, Map<String, List<String>> sites) {
        buf.writeVarInt(sites.size());
        sites.forEach((name, models) -> {
            buf.writeUtf(name);
            buf.writeVarInt(models.size());
            for (String model : models) {
                buf.writeUtf(model);
            }
        });
    }

    public static Map<String, List<String>> readSites(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        int safeSize = Math.min(Math.max(size, 0), 1024);
        Map<String, List<String>> sites = new LinkedHashMap<>();
        for (int i = 0; i < safeSize; i++) {
            String name = buf.readUtf();
            int modelCount = buf.readVarInt();
            int safeModelCount = Math.min(Math.max(modelCount, 0), 1024);
            List<String> models = new java.util.ArrayList<>(Math.max(4, safeModelCount));
            for (int j = 0; j < safeModelCount; j++) {
                models.add(buf.readUtf());
            }
            sites.put(name, models);
        }
        return sites;
    }

    /**
     * 记录一条可疑的包（站点名对不上时用），避免在热路径里刷屏。
     */
    public static void warn(String message, Object... args) {
        TouhouLittleMaid.LOGGER.warn("[Futo] " + message, args);
    }
}

