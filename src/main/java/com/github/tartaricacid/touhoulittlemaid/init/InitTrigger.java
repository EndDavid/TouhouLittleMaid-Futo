package com.github.tartaricacid.touhoulittlemaid.init;

import com.github.tartaricacid.touhoulittlemaid.advancements.rewards.GivePatchouliBookConfigTrigger;
import com.github.tartaricacid.touhoulittlemaid.mixin.CriteriaTriggersAccessor;

/**
 * 判据触发器注册表，移植自上游 {@code init/InitTrigger}（只保留已移植的部分）。
 *
 * <p>本移植版目前只有「首次登录送帕秋莉手册」这一条；
 * 上游的 {@code GiveSmartSlabConfigTrigger} / {@code AltarCraftTrigger} /
 * {@code MaidEventTrigger} 依赖尚未移植的祭坛、进度系统，等对应模块搬过来再补。</p>
 *
 * <p>必须在服务端开始加载数据包（解析进度 JSON）之前完成注册，
 * 所以在主入口里紧跟配置注册之后调用。</p>
 */
public final class InitTrigger {
    public static GivePatchouliBookConfigTrigger GIVE_PATCHOULI_BOOK_CONFIG;

    private InitTrigger() {
    }

    public static void init() {
        // 原版 CriteriaTriggers#register 是 private，走 @Invoker 访问器（见 CriteriaTriggersAccessor）
        GIVE_PATCHOULI_BOOK_CONFIG = CriteriaTriggersAccessor.invokeRegister(new GivePatchouliBookConfigTrigger());
    }
}

