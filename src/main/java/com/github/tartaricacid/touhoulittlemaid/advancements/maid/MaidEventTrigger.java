package com.github.tartaricacid.touhoulittlemaid.advancements.maid;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/**
 * 女仆进度判据，移植自上游 {@code advancements/maid/MaidEventTrigger}。
 *
 * <p>上游把「女仆相关的几十种成就事件」压缩成一个判据：进度 JSON 里写
 * {@code "trigger": "touhou_little_maid:maid/tamed_maid", "conditions": {"event": "switch_task"}}，
 * 代码里 {@code trigger(player, TriggerType.SWITCH_TASK)} 即可命中。
 * 这样加一个新成就只需要写 JSON，不必新增判据类。</p>
 *
 * <p>本次移植引入它，是因为<b>饰品触发的成就本来就挂着它</b>
 * （{@code USE_PROTECT_BAUBLE} / {@code USE_NIMBLE_FABRIC} /
 * {@code USE_ITEM_MAGNET_BAUBLE} / {@code USE_UNDEAD_BAUBLE}）——
 * 少了它，饰品功能本身是完整的，但对应的进度永远点不亮。</p>
 */
public class MaidEventTrigger extends SimpleCriterionTrigger<MaidEventTrigger.Instance> {
    public static final ResourceLocation ID = new ResourceLocation(TouhouLittleMaid.MOD_ID, "maid/tamed_maid");

    public static MaidEventTrigger.Instance create(String eventName) {
        return new MaidEventTrigger.Instance(eventName);
    }

    @Override
    protected Instance createInstance(JsonObject json, EntityPredicate.Composite player,
                                      DeserializationContext conditionsParser) {
        String eventName = GsonHelper.getAsString(json, "event");
        return new MaidEventTrigger.Instance(eventName);
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    public void trigger(ServerPlayer serverPlayer, String eventName) {
        super.trigger(serverPlayer, instance -> instance.matches(eventName));
    }

    public static class Instance extends AbstractCriterionTriggerInstance {
        private final String eventName;

        public Instance(String eventName) {
            super(ID, EntityPredicate.Composite.ANY);
            this.eventName = eventName;
        }

        public boolean matches(String eventNameIn) {
            return this.eventName.equals(eventNameIn);
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject jsonObject = super.serializeToJson(context);
            jsonObject.addProperty("event", this.eventName);
            return jsonObject;
        }
    }
}

