package com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.molang;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.binding.ContextBinding;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.molang.functions.BonePosition;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.molang.functions.BoneRotation;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.molang.functions.BoneScale;
import net.minecraft.client.Minecraft;

/**
 * Yes Steve Model（YSM）兼容的 Molang 绑定，通过 {@code ysm_*} 前缀访问。
 *
 * <p>上游这里注册了十几个函数 / 变量（{@code mod_version}、{@code equipped_enchantment_level}、
 * {@code effect_level}、{@code relative_block_name}、{@code bone_rot} 等），其中多个依赖
 * Forge 的 {@code ForgeMod} 属性与注册表。当前渲染里程碑先把不需要 Forge 的部分接回来，
 * 其余留待联动里程碑补齐，避免动画在找不到变量时整体报错。</p>
 */
public class YSMBinding extends ContextBinding {
    public static final YSMBinding INSTANCE = new YSMBinding();

    private YSMBinding() {
        var("head_yaw", ctx -> ctx.data().netHeadYaw);
        var("head_pitch", ctx -> ctx.data().headPitch);
        var("dimension_name", ctx -> {
            var level = ctx.level();
            return level == null ? "minecraft:overworld" : level.dimension().location().toString();
        });
        // TODO 上游还有 ysm_fps（需要 Minecraft.fps，1.18.2 中为 private，待接入 AccessWidener 后再补）

        // 基础状态量：模型包里的动画大量使用下列变量，缺失会产生大量解析错误
        livingEntityVar("has_mainhand", ctx -> !ctx.entity().getMainHandItem().isEmpty());
        livingEntityVar("has_offhand", ctx -> !ctx.entity().getOffhandItem().isEmpty());
        entityVar("food_level", ctx -> ctx.entity() instanceof net.minecraft.world.entity.player.Player player
                ? player.getFoodData().getFoodLevel() : 0);
        entityVar("is_close_eyes", ctx -> false);

        // 骨骼参数函数（模型包动画用 ysm.bone_rot('LeftM1').x 这类写法驱动骨骼）
        function("bone_rot", new BoneRotation());
        function("bone_pos", new BonePosition());
        function("bone_scale", new BoneScale());
    }
}
