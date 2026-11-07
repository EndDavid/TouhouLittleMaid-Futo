package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataSerializer;

/**
 * 女仆日程表模式，移植自上游 {@code entity/ai/brain/MaidSchedule}。
 *
 * <p>{@code DAY}：06:00~18:00 工作、18:00~22:00 娱乐、22:00~06:00 睡觉；
 * {@code NIGHT}：18:00~06:00 工作、06:00~14:00 睡觉、14:00~18:00 娱乐；
 * {@code ALL}：全天工作。具体的时刻表在 {@code InitEntities} 里用 {@code ScheduleBuilder} 定义。</p>
 */
public enum MaidSchedule {
    // 日程表的模式
    DAY, NIGHT, ALL;

    public static final EntityDataSerializer<MaidSchedule> DATA = new EntityDataSerializer<>() {
        @Override
        public void write(FriendlyByteBuf buf, MaidSchedule value) {
            buf.writeEnum(value);
        }

        @Override
        public MaidSchedule read(FriendlyByteBuf buf) {
            return buf.readEnum(MaidSchedule.class);
        }

        @Override
        public MaidSchedule copy(MaidSchedule value) {
            return value;
        }
    };
}

