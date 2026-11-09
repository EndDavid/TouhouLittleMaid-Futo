package com.github.tartaricacid.touhoulittlemaid.advancements.rewards;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.compat.patchouli.PatchouliCompat;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MiscConfig;
import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 「首次进入世界时送一本手册」用的判据触发器，移植自上游同名类。
 *
 * <p>上游把「送书」做成一条隐藏进度（
 * {@code data/touhou_little_maid/advancements/grant_book_on_first_join.json}）：
 * 判据命中时执行奖励战利品表，往里塞一本
 * {@code patchouli:guide_book}（NBT 指向本模组的手册）。</p>
 *
 * <p>这样做的好处是<b>持久化天然正确</b>：进度存在玩家档案里，送过就不会再送，
 * 也不用自己在玩家 NBT 里记标志位。判据本身每次登录都由
 * {@code event/EnterServerEvent} 主动触发。</p>
 *
 * <p><b>与上游的差异：</b>上游在 JSON 里用 {@code forge:conditional} +
 * {@code forge:mod_loaded} 判断是否加载了 Patchouli；Fabric 没有 Forge 的条件式进度，
 * 因此把这两个前置条件挪进判据的 {@code test} 里 —— 没装 Patchouli 或配置关闭时，
 * 判据直接不成立，效果完全相同。</p>
 */
public class GivePatchouliBookConfigTrigger extends SimpleCriterionTrigger<GivePatchouliBookConfigTrigger.Instance> {
    public static final ResourceLocation ID =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "give_patchouli_book_config");

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
        super.trigger(serverPlayer, instance -> PatchouliCompat.isLoaded() && MiscConfig.givePatchouliBook());
    }

    public static class Instance extends AbstractCriterionTriggerInstance {
        public Instance(EntityPredicate.Composite player) {
            super(ID, player);
        }
    }
}

