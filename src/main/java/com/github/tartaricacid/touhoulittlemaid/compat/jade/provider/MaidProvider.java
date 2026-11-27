package com.github.tartaricacid.touhoulittlemaid.compat.jade.provider;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.schedule.Activity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * 女仆实体的 Jade 提示行：任务 / 日程 / 好感度 / 距离下一级还差多少点 / 是否永久无敌。
 *
 * <p>文案键沿用上游的 {@code top.touhou_little_maid.entity_maid.*}（各 lang json 里本来就有，
 * 是当年 TOP 联动留下的），不需要新增。</p>
 *
 * <p>读的全是 entity data 同步字段（{@code DATA_TASK} / {@code DATA_FAVORABILITY} /
 * {@code SCHEDULE_MODE}），客户端能直接拿到正确值 —— 这一点与上游相同。</p>
 *
 * <h2>两处与上游的差异</h2>
 * <ul>
 *     <li><b>{@code AddJadeInfoEvent}</b>：上游在「任务」行之后 post 这个 Forge 事件，
 *         让附属模组往提示里插自己的行。本移植版没有附属模组扩展体系（连事件总线都是 Fabric 的），
 *         暂不移植；将来要做时在这里补一行回调即可，顺序就在任务行之下。</li>
 *     <li><b>无敌标志</b>：上游读的是自己同步的 {@code DATA_INVULNERABLE}（因为替身地藏的
 *         {@code setInvulnerable} 不会下发到客户端）。本移植版还没有这个同步字段，
 *         直接读 {@code Entity#isInvulnerable()} —— 客户端侧该字段恒为 {@code false}，
 *         所以这一行实际上不会显示。等以后补了同步字段，把这里换成对应 getter 即可。</li>
 * </ul>
 */
public enum MaidProvider implements IEntityComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = TouhouLittleMaid.rl("maid");

    @Override
    public void appendTooltip(ITooltip iTooltip, EntityAccessor entityAccessor, IPluginConfig iPluginConfig) {
        if (entityAccessor.getEntity() instanceof EntityMaid maid) {
            if (!maid.isTame()) {
                return;
            }
            IMaidTask task = maid.getTask();
            iTooltip.add(new TranslatableComponent("top.touhou_little_maid.entity_maid.task").append(task.getName()));
            // 上游此处 post AddJadeInfoEvent（见类注释）；本移植版无扩展体系，故留空。
            iTooltip.add(new TranslatableComponent("top.touhou_little_maid.entity_maid.schedule")
                    .append(getActivityTransText(maid)));
            iTooltip.add(new TranslatableComponent("top.touhou_little_maid.entity_maid.favorability",
                    maid.getFavorabilityManager().getLevel()));
            iTooltip.add(new TranslatableComponent("top.touhou_little_maid.entity_maid.nex_favorability_point",
                    maid.getFavorabilityManager().nextLevelPoint()));
            if (maid.isInvulnerable()) {
                iTooltip.add(new TranslatableComponent("top.touhou_little_maid.entity_maid.invulnerable")
                        .withStyle(ChatFormatting.DARK_PURPLE));
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    private MutableComponent getActivityTransText(EntityMaid maid) {
        return getActivityTransText(maid.getScheduleDetail());
    }

    private MutableComponent getActivityTransText(Activity activity) {
        return new TranslatableComponent("gui.touhou_little_maid.activity." + activity.getName());
    }
}

