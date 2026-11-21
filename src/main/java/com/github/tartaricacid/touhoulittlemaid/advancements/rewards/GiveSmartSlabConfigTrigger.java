package com.github.tartaricacid.touhoulittlemaid.advancements.rewards;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MiscConfig;
import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
/**
 * 「首次进入世界时送一个魂符」用的判据触发器，移植自上游同名类。
 *
 * <p>做法与 {@link GivePatchouliBookConfigTrigger} 完全一致：
 * 送东西这件事做成一条隐藏进度
 * （{@code data/touhou_little_maid/advancements/give_smart_slab.json}），
 * 判据命中时执行奖励战利品表，给出一个 {@code smart_slab_init}。</p>
 *
 * <p><b>「新玩家」是怎么判定的：</b>靠进度本身。进度存在玩家档案
 * （{@code world/playerdata/<uuid>.dat}）里，送出过一次就永远存在，
 * 所以只有<b>档案里还没有这条进度</b>的玩家（也就是这个世界里的新玩家）才会拿到 ——
 * 与「玩家数据文件是否存在」是同一件事，但不需要自己去碰文件。</p>
 *
 * <p>判据的 {@code test} 里读配置：{@code MiscConfig.GIVE_SMART_SLAB}
 * （{@code GiveSoulSpell}，默认开）。配置关掉时判据不成立，进度也不会被点亮，
 * 之后再把配置打开、该玩家重新登录时仍然会发 —— 与上游语义一致。</p>
 */
public class GiveSmartSlabConfigTrigger extends SimpleCriterionTrigger<GiveSmartSlabConfigTrigger.Instance> {
    public static final ResourceLocation ID =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "give_smart_slab_config");
    @Override
    protected Instance createInstance(JsonObject json, EntityPredicate.Composite entityPredicate,
                                      DeserializationContext conditionsParser) {
        return new Instance(entityPredicate);
    }
    @Override
    public ResourceLocation getId() {
        return ID;
    }
    public void trigger(ServerPlayer serverPlayer) {
        super.trigger(serverPlayer, instance -> MiscConfig.giveSmartSlab());
    }
    public static class Instance extends AbstractCriterionTriggerInstance {
        public Instance(EntityPredicate.Composite player) {
            super(ID, player);
        }
    }
}
