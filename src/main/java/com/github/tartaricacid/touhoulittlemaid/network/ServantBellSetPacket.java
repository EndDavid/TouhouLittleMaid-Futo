package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.ItemServantBell;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * 仆从铃的「绑定女仆」C2S 包（上游 {@code ServantBellSetMessage}）。
 *
 * <p>客户端在 {@code ServantBellSetScreen} 里点确定时发出，载荷是
 * <b>女仆实体 id</b> + <b>提示文本</b>。服务端把它写进玩家主手那枚铃的 NBT，
 * 之后这枚铃就只认这一只女仆。</p>
 *
 * <h2>为什么这个动作必须走网络包</h2>
 * <p>{@code ServantBellUuid} / {@code ServantBellTip} 是<b>物品 NBT</b>，
 * 不属于 {@code SynchedEntityData}，客户端改了自己那份既不会被同步、
 * 也到不了服务端。而且「能不能绑定」需要服务端复核归属，
 * 纯客户端逻辑可以被伪造。</p>
 *
 * <p>只有这一件事需要联网：「打开输入框」不需要 —— 客户端自己就知道
 * 刚用铃右击了谁，上游也是直接在客户端开界面的（见
 * {@code ItemServantBell#interactLivingEntity}）。</p>
 */
public final class ServantBellSetPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "servant_bell");

    /**
     * C2S：把主手那枚铃绑定到 {@code entityId}。
     */
    public static final byte BIND = 0;

    /**
     * 提示文本的长度上限。上游用无参 {@code readUtf()}（默认 32767），
     * 这里收窄到 64：它只是显示在物品名字上的一行短提示，
     * 放宽等于白送一个放大攻击的入口。
     */
    public static final int MAX_TIP_LENGTH = 64;

    private ServantBellSetPacket() {
    }

    /**
     * 服务端注册接收器（在主入口调用）。
     */
    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    // 缓冲区出回调即失效，所以字段必须在 execute 之前读完
                    byte action = buf.readByte();
                    int entityId = buf.readInt();
                    String tip = buf.readUtf(MAX_TIP_LENGTH);
                    if (action != BIND) {
                        return;
                    }
                    server.execute(() -> handleBind(player, entityId, tip));
                });
    }

    private static void handleBind(ServerPlayer player, int entityId, String tip) {
        Entity entity = player.level.getEntity(entityId);
        if (!(entity instanceof EntityMaid maid) || !maid.isOwnedByPlayer(player)) {
            return;
        }
        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof ItemServantBell)) {
            // 上游直接往 getMainHandItem() 的 NBT 里写，不检查手里是什么。
            // 这里加一道：否则这个 NBT 可以塞进任何物品，而那件物品的
            // tooltip / getName 并不认识它（无害，但没有任何意义）。
            return;
        }
        ItemServantBell.recordMaidInfo(mainHand, maid.getUUID(), tip);
        // 物品 NBT 变了要主动同步一次，否则客户端手里那枚铃要等下一次
        // 背包全量同步（开容器 / 丢出再捡起）才更新，表现是「绑上了但名字没变」
        player.inventoryMenu.broadcastChanges();
    }
}

