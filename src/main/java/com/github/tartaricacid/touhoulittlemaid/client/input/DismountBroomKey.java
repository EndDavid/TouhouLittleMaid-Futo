package com.github.tartaricacid.touhoulittlemaid.client.input;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom;
import com.github.tartaricacid.touhoulittlemaid.network.DismountBroomPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

/**
 * 「下扫帚」按键，移植自上游 1.20.1 的 {@code client/input/DismountBroomKey}。
 *
 * <p>默认 <b>C</b>，分类 {@code key.category.touhou_little_maid}，
 * 玩家可以在「选项 → 控制 → 按键绑定」里改 —— 与上游一致。</p>
 *
 * <p>上游需要它，是因为扫帚上 <b>Shift 是下降、空格是上升</b>
 * （见 {@code EntityBroom#rideControl}），原版那套「按 Shift 下车」被占用掉了。
 * 本移植版最初漏掉了这一整个按键，结果骑上去就下不来。</p>
 *
 * <h2>与上游的差异（都是框架差异）</h2>
 * <ul>
 *     <li>注册用 Fabric 的 {@link KeyBindingHelper#registerKeyBinding}；
 *         上游是直接在类里 {@code new KeyMapping(...)} 靠 Forge 的
 *         {@code @Mod.EventBusSubscriber} 自动登记。</li>
 *     <li>触发用 {@link ClientTickEvents#END_CLIENT_TICK} + {@code consumeClick()}；
 *         上游订阅 {@code InputEvent.Key} 并在 <b>RELEASE</b> 时才发包。
 *         {@code consumeClick()} 本身就是「一次按下只返回一次 true」，
 *         不需要再判 {@code GLFW_RELEASE}，也不会长按连发。</li>
 *     <li>上游用 {@code KeyConflictContext.IN_GAME} / {@code KeyModifier.NONE}
 *         限定只在游戏内生效；Fabric 没有这两样，所以这里自己判
 *         「没有打开任何界面、鼠标被抓取」—— 与上游那段 {@code isInGame()} 等价。</li>
 * </ul>
 */
public final class DismountBroomKey {
    /**
     * 分类 id 与上游逐字相同，因此中英文 lang 里已有的那一条可以直接复用。
     */
    private static final String CATEGORY = "key.category.touhou_little_maid";

    private static KeyMapping dismountKey;

    private DismountBroomKey() {
    }

    public static void init() {
        dismountKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.touhou_little_maid.dismount.desc",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // consumeClick() 会把手里的「点击计数」清掉，所以必须先取再判
            if (!dismountKey.consumeClick()) {
                return;
            }
            if (!isInGame(client)) {
                return;
            }
            LocalPlayer player = client.player;
            if (player == null || player.isSpectator()) {
                return;
            }
            // 只在真的骑着扫帚时发包：平时按 C 不该给服务端添流量
            if (player.getVehicle() instanceof EntityBroom broom) {
                DismountBroomPacket.send(broom.getId());
            }
        });
    }

    /**
     * 与上游 {@code isInGame()} 等价：不能是加载界面、不能开着任何界面、鼠标被抓取。
     *
     * <p>少了这些判断，在聊天栏里打一个带 c 的单词就会把玩家从扫帚上甩下来。</p>
     */
    private static boolean isInGame(Minecraft minecraft) {
        return minecraft.getOverlay() == null
                && minecraft.screen == null
                && minecraft.mouseHandler.isMouseGrabbed()
                && minecraft.isWindowActive();
    }
}

