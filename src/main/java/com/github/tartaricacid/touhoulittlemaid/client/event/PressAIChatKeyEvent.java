package com.github.tartaricacid.touhoulittlemaid.client.event;

import com.github.tartaricacid.touhoulittlemaid.client.screen.ai.AIChatScreen;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Sets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * 「对着可聊天的女仆按聊天键」→ 打开 AI 聊天界面。
 * 移植自上游 client/event/PressAIChatKeyEvent。
 *
 * <h2>为什么复用原版聊天键</h2>
 * <p>上游复用的是 {@code Minecraft.options.keyChat}（默认 T），
 * 而不是新注册一个键。这样不占按键表，玩家也不用额外配置；
 * 只有「准星正指着一只已归属自己、且模型有角色设定的女仆」时才拦截，
 * 其余情况 T 仍然是打开聊天栏。</p>
 *
 * <h2>与上游的差异（框架差异）</h2>
 * <ul>
 *     <li>上游订阅 Forge 的 {@code InputEvent.KeyInputEvent}，在 GLFW 回调里就判定，
 *         能保证抢在原版聊天栏之前。Fabric 没有这个事件，这里改用
 *         {@code ClientTickEvents.START_CLIENT_TICK}：它在 {@code Minecraft.tick()} 之前执行，
 *         而原版打开聊天栏正是 {@code Minecraft.tick()} 里 {@code keyChat.consumeClick()} 做的，
 *         因此我们先把这次点击取走，原版就拿不到，聊天栏不会弹出来。</li>
 *     <li><b>顺序很关键：先判「是否命中可聊天女仆」，再 consumeClick。</b>
 *         反过来的话，玩家指着普通方块按 T 时点击已被吃掉，聊天栏就打不开了。</li>
 *     <li>上游的 {@code KeyModifier.getActiveModifier()} 判断（Shift+T 之类）在 Fabric 没有等价物，
 *         省略：只要没有其它模组把 T 绑成组合键，行为一致。</li>
 * </ul>
 */
public final class PressAIChatKeyEvent {
    /**
     * 有角色设定的模型 id 集合。由 SyncAiSettingPacket 在登录 / reload 时填充。
     */
    public static final Set<String> CAN_CHAT_MAID_IDS = Sets.newHashSet();

    private PressAIChatKeyEvent() {
    }

    public static void init() {
        ClientTickEvents.START_CLIENT_TICK.register(PressAIChatKeyEvent::onClientTick);
    }

    private static void onClientTick(Minecraft minecraft) {
        if (!isInGame(minecraft)) {
            return;
        }
        EntityMaid maid = maidCheck(minecraft);
        if (maid == null) {
            // 没有命中：这次点击留给原版聊天栏
            return;
        }
        KeyMapping keyChat = minecraft.options.keyChat;
        if (keyChat == null || !keyChat.consumeClick()) {
            return;
        }
        minecraft.setScreen(new AIChatScreen(maid));
    }

    /**
     * 准星指向的女仆是否可聊天。
     *
     * <p>四个条件与上游逐条一致：玩家在场且不是观察者、准星命中实体、
     * 那个实体是女仆、她是这位玩家的、且她的模型有角色设定。</p>
     */
    @Nullable
    private static EntityMaid maidCheck(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || player.isSpectator()) {
            return null;
        }
        HitResult hitResult = minecraft.hitResult;
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return null;
        }
        if (!(entityHitResult.getEntity() instanceof EntityMaid maid)) {
            return null;
        }
        if (!maid.isOwnedByPlayer(player)) {
            return null;
        }
        if (!CAN_CHAT_MAID_IDS.contains(maid.getModelId())) {
            return null;
        }
        return maid;
    }

    /**
     * 与上游 {@code isInGame()} 等价：不在加载界面、没开着任何界面、鼠标被抓取、窗口激活。
     */
    private static boolean isInGame(Minecraft minecraft) {
        return minecraft.getOverlay() == null
                && minecraft.screen == null
                && minecraft.mouseHandler.isMouseGrabbed()
                && minecraft.isWindowActive();
    }
}