package com.github.tartaricacid.touhoulittlemaid.advancements.altar;

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
 * 祭坛合成判据，移植自上游 {@code advancements/altar/AltarCraftTrigger}。
 *
 * <p>与 {@code MaidEventTrigger} 的区别：这个判据<b>带条件</b> ——
 * 进度 JSON 里要写 {@code "recipe_id": "touhou_little_maid:altar/spawn_box"}，
 * 只有合成了那一条配方才会点亮。上游用它区分「第一次合出女仆」之类的细分进度。</p>
 *
 * <p>不注册也<b>不会崩</b>：只是 {@link #trigger} 那一行永远点不亮任何进度。
 * 但注册必须早于数据包解析（进度 JSON 会按 id 反查判据），
 * 所以挂在主入口的 {@code InitTrigger.init()} 里。</p>
 */
public class AltarCraftTrigger extends SimpleCriterionTrigger<AltarCraftTrigger.Instance> {
    public static final ResourceLocation ID = new ResourceLocation(TouhouLittleMaid.MOD_ID, "altar/altar_craft");

    @Override
    protected AltarCraftTrigger.Instance createInstance(JsonObject json, EntityPredicate.Composite player,
                                                       DeserializationContext conditionsParser) {
        ResourceLocation recipeId = new ResourceLocation(GsonHelper.getAsString(json, "recipe_id"));
        return new AltarCraftTrigger.Instance(player, recipeId);
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    public void trigger(ServerPlayer serverPlayer, ResourceLocation recipeId) {
        super.trigger(serverPlayer, instance -> instance.matches(recipeId));
    }

    public static class Instance extends AbstractCriterionTriggerInstance {
        private final ResourceLocation recipeId;

        public Instance(EntityPredicate.Composite player, ResourceLocation recipeId) {
            super(ID, player);
            this.recipeId = recipeId;
        }

        public static AltarCraftTrigger.Instance recipe(ResourceLocation recipeId) {
            return new AltarCraftTrigger.Instance(EntityPredicate.Composite.ANY, recipeId);
        }

        public boolean matches(ResourceLocation recipeIdIn) {
            return this.recipeId.equals(recipeIdIn);
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject jsonObject = super.serializeToJson(context);
            jsonObject.addProperty("recipe_id", this.recipeId.toString());
            return jsonObject;
        }
    }
}

