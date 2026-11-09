package com.github.tartaricacid.touhoulittlemaid.compat.patchouli;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.event.client.OpenPatchouliBookEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import vazkii.patchouli.api.PatchouliAPI;

/**
 * 默认的「点侧边栏按钮 → 打开本模组手册」监听器，移植自上游同名类。
 *
 * <p>只有任务 uid 的命名空间是 {@code touhou_little_maid} 时才处理；其它模组注册的
 * 任务应当由它们自己的监听器打开自己的书（这正是上游把开书行为拆成事件的原因）。</p>
 *
 * <p>本类引用了 {@code vazkii.patchouli.api.PatchouliAPI}，因此只能在确认
 * Patchouli 已安装后再加载（见 {@link PatchouliCompat#init()}）。</p>
 */
public final class OpenDefaultBook {
    @SubscribeEvent
    public void onPatchouliBookEvent(OpenPatchouliBookEvent event) {
        ResourceLocation uid = event.getTask().getUid();
        if (uid.getNamespace().equals(TouhouLittleMaid.MOD_ID)) {
            // openBookGUI(ResourceLocation) 是 1.18.2 的客户端重载：直接打开手册、回到上次的页面
            PatchouliAPI.get().openBookGUI(PatchouliCompat.BOOK_ID);
        }
    }
}
