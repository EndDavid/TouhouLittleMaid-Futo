package com.github.tartaricacid.touhoulittlemaid.util;

import javax.annotation.Nullable;

/**
 * 字节数组 ↔ 布尔数组，移植自上游 {@code util/BytesBooleansConvert}。
 *
 * <p>隙间的「槽位配置」（哪些槽位<b>不</b>允许传输）在物品 NBT 里存成
 * {@code byte[]}（{@code 1} = 该槽位冻结），进游戏逻辑时转成 {@code boolean[]}。
 * 用 byte 而不是 boolean 是因为 NBT 没有布尔数组类型。</p>
 *
 * <p><b>注意 {@code slotNum} 的实际含义：</b>它只在 {@code bytes} 为 {@code null}
 * 时用来决定输出长度；传入了数组就按数组自身长度返回。上游如此，
 * 这里也照抄 —— 调用方（{@code WirelessIOBauble}）正是靠这一点来兼容
 * 「老存档里存了较短的数组」这种情况。</p>
 */
public final class BytesBooleansConvert {
    private BytesBooleansConvert() {
    }

    public static boolean[] bytes2Booleans(@Nullable byte[] bytes, int slotNum) {
        if (bytes == null) {
            bytes = new byte[slotNum];
        }
        boolean[] out = new boolean[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            out[i] = (bytes[i] != 0);
        }
        return out;
    }

    public static byte[] booleans2Bytes(boolean[] booleans) {
        byte[] out = new byte[booleans.length];
        for (int i = 0; i < booleans.length; i++) {
            out[i] = (byte) (booleans[i] ? 1 : 0);
        }
        return out;
    }
}

