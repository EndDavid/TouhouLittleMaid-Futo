package com.github.tartaricacid.touhoulittlemaid.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;

/**
 * 女仆击杀记录的事件入口，对应上游 {@code event/EntityDeathEvent}。
 *
 * <p>上游监听 Forge 的 {@code LivingDeathEvent}，用 {@code source.getEntity()} 判断
 * 「谁杀的」。</p>
 *
 * <p><b>为什么不是 {@code ServerLivingEntityEvents.AFTER_DEATH}：</b>
 * 那是 Fabric API <b>1.6.0 之后</b>才有的类，本工程按 1.18.2 的 fabric-api 0.76
 * 解析到的是 fabric-entity-events-v1 <b>1.4.x</b>，里面只有
 * {@link ServerEntityCombatEvents#AFTER_KILLED_OTHER_ENTITY}。
 * 这个事件恰好就是「某个生物击杀了另一个生物」——语义与
 * {@code LivingDeathEvent + source.getEntity()} 完全一致，而且它本来就只在
 * 服务端、只在击杀真的成立时触发。</p>
 */
public final class MaidKillRecordEvent {
    private MaidKillRecordEvent() {
    }

    public static void init() {
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed) -> {
            // killer 是「动手的那一方」。它可能是玩家、可能是别的怪，只有女仆才算她的击杀。
            if (killer instanceof EntityMaid maid) {
                maid.getKillRecordManager().onTargetDeath(maid, killed);
            }
        });
    }
}

