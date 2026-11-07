package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.github.tartaricacid.touhoulittlemaid.entity.data.AttackListData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.PickType;
import com.github.tartaricacid.touhoulittlemaid.network.MaidConfigPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端发送女仆设置变更的薄封装。
 *
 * <p>把 {@code ClientPlayNetworking} 的引用限制在这个类里，这样
 * {@link MaidConfigPacket} 可以放在 common 包中而不会让专用服务端
 * 去解析客户端类。</p>
 *
 * <p>所有这些方法都<b>只发意图</b>，不在本地直接改实体数据：目标字段都是
 * {@code SynchedEntityData}，本地先改会被服务端下一帧的同步值覆盖，
 * 表现是「点了按钮闪一下又弹回去」。</p>
 */
public final class MaidClientPacketSender {
    private MaidClientPacketSender() {
    }

    public static void sendConfig(byte action, boolean value, int entityId) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeByte(action);
        buf.writeInt(entityId);
        buf.writeBoolean(value);
        ClientPlayNetworking.send(MaidConfigPacket.CHANNEL, buf);
    }

    /**
     * 切换工作模式。
     */
    public static void sendTask(ResourceLocation taskUid, int entityId) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeByte(MaidConfigPacket.TASK);
        buf.writeInt(entityId);
        buf.writeResourceLocation(taskUid);
        ClientPlayNetworking.send(MaidConfigPacket.CHANNEL, buf);
    }

    /**
     * 切换页签。服务端会关掉当前菜单、按 {@code tab} 打开另一个。
     */
    public static void sendTab(int tab, int entityId) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeByte(MaidConfigPacket.TAB);
        buf.writeInt(entityId);
        buf.writeInt(tab);
        ClientPlayNetworking.send(MaidConfigPacket.CHANNEL, buf);
    }

    /**
     * 语音频率（0.0 ~ 1.0，服务端还会再夹一次）。
     */
    public static void sendSoundFreq(float value, int entityId) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeByte(MaidConfigPacket.SOUND_FREQ);
        buf.writeInt(entityId);
        buf.writeFloat(value);
        ClientPlayNetworking.send(MaidConfigPacket.CHANNEL, buf);
    }

    /**
     * 拾取类型。
     */
    public static void sendPickType(PickType type, int entityId) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeByte(MaidConfigPacket.PICK_TYPE);
        buf.writeInt(entityId);
        buf.writeUtf(type.getId());
        ClientPlayNetworking.send(MaidConfigPacket.CHANNEL, buf);
    }

    /**
     * 提交整份攻击名单（关闭「任务设置」界面时发一次）。
     */
    public static void sendAttackList(AttackListData data, int entityId) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeByte(MaidConfigPacket.ATTACK_LIST);
        buf.writeInt(entityId);
        data.writeToBuf(buf);
        ClientPlayNetworking.send(MaidConfigPacket.CHANNEL, buf);
    }

    /**
     * 提交命名牌的新名字（{@code alwaysShow} 决定名字是否常显）。
     *
     * <p>名字只在服务端写入：{@code setCustomName} 本身不是同步数据，
     * 客户端自己改名会被下一次实体同步覆盖，而且改名还要消耗命名牌。</p>
     */
    public static void sendNameTag(String name, boolean alwaysShow, int entityId) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeByte(MaidConfigPacket.NAME_TAG);
        buf.writeInt(entityId);
        buf.writeUtf(name, 32);
        buf.writeBoolean(alwaysShow);
        ClientPlayNetworking.send(MaidConfigPacket.CHANNEL, buf);
    }
}
