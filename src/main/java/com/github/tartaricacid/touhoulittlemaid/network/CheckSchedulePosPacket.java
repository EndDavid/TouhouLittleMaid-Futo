package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 「为什么这次开不了居家模式」—— S2C，移植自上游
 * {@code network/message/CheckSchedulePosMessage}。
 *
 * <h2>它解决的是什么问题</h2>
 * <p>在女仆界面上点 Home 开关有两个会失败的前提：</p>
 * <ol>
 *     <li>罗盘记下的坐标在<b>别的维度</b>（原版下界 / 末地和主世界坐标互不相通）；</li>
 *     <li>三个点距离她<b>超过 32 格</b>（她会立刻被 {@code SchedulePos#tick} 传送走，
 *         玩家看到的是「刚开 Home 就瞬移不见了」）。</li>
 * </ol>
 * <p>这两种情况服务端都会拒绝开启，但拒绝本身在客户端是无声的 —— 开关会弹回原状。
 * 这个包就是那句解释，客户端收到后盖一层小提示界面。</p>
 *
 * <h2>为什么走独立的包而不是聊天栏</h2>
 * <p>上游没有只写一行字，而是盖了一个界面、上面有「返回」按钮 —— 因为玩家此刻
 * 正开着女仆界面，聊天栏被界面挡住看不见。这里保持同样的做法。</p>
 */
public final class CheckSchedulePosPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "check_schedule_pos");

    private CheckSchedulePosPacket() {
    }

    /**
     * 服务端调用：给这位玩家弹一条「作息坐标检查未通过」的提示。
     */
    public static void send(ServerPlayer player, Component tips) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeComponent(tips);
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }
}
