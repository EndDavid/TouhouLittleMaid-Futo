package com.github.tartaricacid.touhoulittlemaid.api.bauble;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;

import java.util.Random;

/**
 * 女仆饰品的行为接口，移植自上游 {@code api/bauble/IMaidBauble}。
 *
 * <p>一个「饰品」由两部分构成：</p>
 * <ol>
 *     <li><b>物品</b>（任意 {@code Item}）—— 决定贴图、耐久、能否进饰品栏；</li>
 *     <li><b>行为</b>（本接口的实现）—— 通过 {@code BaubleManager} 绑定到物品上。</li>
 * </ol>
 *
 * <p>之所以把行为和物品拆开，是因为行为需要监听 Forge 事件（受伤 / 死亡 / 播语音），
 * 而物品对象是<b>单例</b>、注册后就固定了；把监听器注册在物品对象上会让「谁注册了什么」
 * 散落在注册表里，反而不如集中到 {@code BaubleManager} 一张表里清楚。</p>
 */
public interface IMaidBauble {
    Random RANDOM = new Random();

    /**
     * 女仆每 tick 调用一次（由 {@code EntityMaid#tick} 遍历饰品栏驱动）。
     *
     * <p>注意是<b>每只女仆、每件饰品、每 tick</b> 都会进这里 ——
     * 实现里必须自己按 {@code maid.tickCount % n} 降频，不能无脑做重活。</p>
     *
     * @param maid       女仆
     * @param baubleItem 饰品本身
     */
    default void onTick(EntityMaid maid, ItemStack baubleItem) {
    }

    /**
     * 挂着这件饰品时女仆头顶聊天气泡的 id。
     *
     * <p>本移植版的聊天气泡（{@code ChatBubble}）子系统尚未移植，
     * 因此该返回值目前没有任何消费方；保留它是为了与上游接口一致，
     * 等气泡系统搬过来即可直接生效。</p>
     *
     * @return 气泡 id，默认空串（无气泡）
     */
    default String getChatBubbleId() {
        return StringUtils.EMPTY;
    }
}

