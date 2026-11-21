package com.github.tartaricacid.touhoulittlemaid.init;

import com.github.tartaricacid.touhoulittlemaid.advancements.altar.AltarCraftTrigger;
import com.github.tartaricacid.touhoulittlemaid.advancements.maid.MaidEventTrigger;
import com.github.tartaricacid.touhoulittlemaid.advancements.rewards.GivePatchouliBookConfigTrigger;
import com.github.tartaricacid.touhoulittlemaid.advancements.rewards.GiveSmartSlabConfigTrigger;
import com.github.tartaricacid.touhoulittlemaid.mixin.CriteriaTriggersAccessor;

/**
 * 判据触发器注册表，移植自上游 {@code init/InitTrigger}（只保留已移植的部分）。
 *
 * <p>四条：「首次登录送帕秋莉手册」、「首次登录送魂符」、「女仆事件」与「祭坛合成」。</p>
 *
 * <p>必须在服务端开始加载数据包（解析进度 JSON）之前完成注册，
 * 所以在主入口里紧跟配置注册之后调用。</p>
 */
public final class InitTrigger {
    public static GivePatchouliBookConfigTrigger GIVE_PATCHOULI_BOOK_CONFIG;
    /**
     * 魂符判据（上游 {@code InitTrigger.GIVE_SMART_SLAB_CONFIG}）。
     *
     * <p>同样是「敲一下让隐藏进度去查条件」，命中后由战利品表发放 {@code smart_slab_init}。</p>
     */
    public static GiveSmartSlabConfigTrigger GIVE_SMART_SLAB_CONFIG;
    /**
     * 女仆事件判据（上游 {@code InitTrigger.MAID_EVENT}）。
     *
     * <p>饰品效果触发时会打这个判据；未注册时 {@code trigger(...)} 会是空指针，
     * 所以必须先于任何女仆 tick 完成注册。</p>
     */
    public static MaidEventTrigger MAID_EVENT;

    /**
     * 祭坛合成判据（上游 {@code InitTrigger.ALTAR_CRAFT}）。
     *
     * <p>进度 JSON 里带 {@code recipe_id} 条件，用来区分「第一次合出女仆」之类的细分成就。</p>
     */
    public static AltarCraftTrigger ALTAR_CRAFT;

    private InitTrigger() {
    }

    public static void init() {
        // 原版 CriteriaTriggers#register 是 private，走 @Invoker 访问器（见 CriteriaTriggersAccessor）
        GIVE_PATCHOULI_BOOK_CONFIG = CriteriaTriggersAccessor.invokeRegister(new GivePatchouliBookConfigTrigger());
        GIVE_SMART_SLAB_CONFIG = CriteriaTriggersAccessor.invokeRegister(new GiveSmartSlabConfigTrigger());
        MAID_EVENT = CriteriaTriggersAccessor.invokeRegister(new MaidEventTrigger());
        ALTAR_CRAFT = CriteriaTriggersAccessor.invokeRegister(new AltarCraftTrigger());
    }
}
