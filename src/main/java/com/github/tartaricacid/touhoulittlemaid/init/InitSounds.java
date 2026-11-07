package com.github.tartaricacid.touhoulittlemaid.init;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * 音效事件注册。
 *
 * <p>键名与资源包里的 {@code sounds.json} 完全一致，因此不需要额外配置就能播到
 * 原模组的语音（{@code assets/touhou_little_maid/sounds/maid/...}）与妖精音效。</p>
 *
 * <p><b>注意 {@code maid_voice.*} 与 {@code maid.ai.*} 两套前缀并存：</b>
 * 前者是上游 {@code InitSounds} 里的历史命名（{@code tamed} / {@code hurt} / {@code death}），
 * 它们是独立注册的键；后者本身就是 {@code sounds.json} 的键名。
 * 两边都直接对应 JSON 里的键，改任何一边都会静音。</p>
 */
public final class InitSounds {
    // ------------------------------------------------------------------ 女仆语音
    public static final SoundEvent MAID_TAMED = register("maid_voice.tamed");
    public static final SoundEvent MAID_HURT = register("maid_voice.hurt");
    public static final SoundEvent MAID_DEATH = register("maid_voice.death");
    public static final SoundEvent MAID_HURT_FIRE = register("maid.ai.hurt_fire");
    public static final SoundEvent MAID_ITEM_GET = register("maid.ai.item_get");
    public static final SoundEvent MAID_FIND_TARGET = register("maid.ai.find_target");

    // ------------------------------------------------------------------ 工作模式语音
    public static final SoundEvent MAID_MODE_IDLE = register("maid.mode.idle");
    /** 攻击模式下的工作音效（上游 {@code task.touhou_little_maid.attack} 使用）。 */
    public static final SoundEvent MAID_ATTACK = register("maid.mode.attack");
    /** 远程攻击模式下的工作音效。 */
    public static final SoundEvent MAID_RANGE_ATTACK = register("maid.mode.range_attack");
    /** 弹幕攻击模式下的工作音效（上游 {@code MAID_DANMAKU_ATTACK}）。 */
    public static final SoundEvent MAID_DANMAKU_ATTACK = register("maid.mode.danmaku_attack");
    public static final SoundEvent MAID_FARM = register("maid.mode.farm");
    public static final SoundEvent MAID_FEED = register("maid.mode.feed");
    public static final SoundEvent MAID_SHEARS = register("maid.mode.shears");
    public static final SoundEvent MAID_MILK = register("maid.mode.milk");
    public static final SoundEvent MAID_TORCH = register("maid.mode.torch");
    public static final SoundEvent MAID_FEED_ANIMAL = register("maid.mode.feed_animal");
    public static final SoundEvent MAID_EXTINGUISHING = register("maid.mode.extinguishing");
    /** 除雪模式（上游 {@code MAID_REMOVE_SNOW}，键名 {@code maid.mode.snow}）。 */
    public static final SoundEvent MAID_REMOVE_SNOW = register("maid.mode.snow");

    // ------------------------------------------------------------------ 环境语音
    public static final SoundEvent MAID_MORNING = register("maid.environment.morning");
    public static final SoundEvent MAID_NIGHT = register("maid.environment.night");
    public static final SoundEvent MAID_RAIN = register("maid.environment.rain");
    public static final SoundEvent MAID_SNOW = register("maid.environment.snow");
    public static final SoundEvent MAID_COLD = register("maid.environment.cold");
    public static final SoundEvent MAID_HOT = register("maid.environment.hot");

    // ------------------------------------------------------------------ 道具音效

    /**
     * 相机快门声（上游 {@code InitSounds.CAMERA_USE}）。
     *
     * <p>{@code sounds.json} 里的键是 {@code item.camera_use}，
     * 音源为 {@code sounds/item/camera_use.ogg}，分类是 {@code voice}
     * （与其它女仆语音同槽，会被玩家的「语音」音量控制）。</p>
     */
    public static final SoundEvent CAMERA_USE = register("item.camera_use");

    // ------------------------------------------------------------------ 女仆妖精
    /**
     * 妖精音效。{@code sounds.json} 里直接指向原版兔子音效
     * （{@code minecraft:mob/rabbit/*}）—— 上游也是这么做的，不是漏配。
     */
    public static final SoundEvent FAIRY_AMBIENT = register("entity.fairy.ambient");
    public static final SoundEvent FAIRY_HURT = register("entity.fairy.hurt");
    public static final SoundEvent FAIRY_DEATH = register("entity.fairy.death");

    private InitSounds() {
    }

    public static void init() {
        // 静态字段即完成注册
    }

    private static SoundEvent register(String name) {
        ResourceLocation id = TouhouLittleMaid.rl(name);
        return Registry.register(Registry.SOUND_EVENT, id, new SoundEvent(id));
    }
}

