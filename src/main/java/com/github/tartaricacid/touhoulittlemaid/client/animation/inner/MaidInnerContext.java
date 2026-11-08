package com.github.tartaricacid.touhoulittlemaid.client.animation.inner;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * 一次「内建动画」求值所需的上下文。
 *
 * <p>对应上游 {@code IAnimation#setRotationAngles} 的那六个 float 加上 {@code modelMap}。
 * 区别是这里直接持有 {@link AnimatedGeoModel} —— 本移植版的骨骼表已经是
 * {@code Map<String, AnimatedGeoBone>}，不需要再包一层 {@code ModelRendererWrapper}。</p>
 *
 * <p><b>关于 {@code getInitRotateAngleX/Y/Z}</b>：上游那套 wrapper 缓存了模型 JSON 里的初始
 * 旋转，内建动画写作「初值 + 偏移」。这里用
 * {@link AnimatedGeoBone#getInitialSnapshot()} 的 {@code rotationValueX/Y/Z} 提供同样的初值，
 * 语义完全一致（旧版模型这些骨头的初值通常是 0，但少数模型有非零初值，
 * 不加上去就会把模型拧歪）。</p>
 */
public final class MaidInnerContext {
    private final IMaid maid;
    private final AnimatedGeoModel model;
    private final float limbSwing;
    private final float limbSwingAmount;
    private final float ageInTicks;
    private final float netHeadYaw;
    private final float headPitch;

    public MaidInnerContext(IMaid maid, AnimatedGeoModel model, float limbSwing, float limbSwingAmount,
                            float ageInTicks, float netHeadYaw, float headPitch) {
        this.maid = maid;
        this.model = model;
        this.limbSwing = limbSwing;
        this.limbSwingAmount = limbSwingAmount;
        this.ageInTicks = ageInTicks;
        this.netHeadYaw = netHeadYaw;
        this.headPitch = headPitch;
    }

    public IMaid maid() {
        return this.maid;
    }

    public Mob mob() {
        return this.maid.asEntity();
    }

    public float limbSwing() {
        return this.limbSwing;
    }

    public float limbSwingAmount() {
        return this.limbSwingAmount;
    }

    public float ageInTicks() {
        return this.ageInTicks;
    }

    public float netHeadYaw() {
        return this.netHeadYaw;
    }

    public float headPitch() {
        return this.headPitch;
    }

    /** 按名字取骨骼；不存在返回 {@code null}（上游 {@code modelMap.get} 的语义）。 */
    @Nullable
    public AnimatedGeoBone bone(String name) {
        return this.model.bones().get(name);
    }

    /** 初值（度转弧度的上游写法在这里不需要，模型里的初值本身就是弧度）。 */
    public static float initX(AnimatedGeoBone bone) {
        return bone.getInitialSnapshot().rotationValueX;
    }

    public static float initY(AnimatedGeoBone bone) {
        return bone.getInitialSnapshot().rotationValueY;
    }

    public static float initZ(AnimatedGeoBone bone) {
        return bone.getInitialSnapshot().rotationValueZ;
    }

    /** 上游 {@code setOffsetY}：沿 Y 平移骨骼（模型单位，1 = 1/16 方块）。 */
    public static void setOffsetY(AnimatedGeoBone bone, float offset) {
        bone.setPositionY(bone.getInitialSnapshot().positionOffsetY + offset);
    }
}

