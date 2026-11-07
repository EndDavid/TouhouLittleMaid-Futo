package com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.processor.ILocationBone;

import java.util.Collections;
import java.util.List;

public interface ILocationModel {
    default List<? extends ILocationBone> leftHandBones() {
        return Collections.emptyList();
    }

    /**
     * 按骨骼名取「该骨骼 → 根」的完整父级链（第一个元素是骨骼本身，最后一个是根）。
     *
     * <p>与 {@link #leftHandBones()} 这类固定 locator 的区别：这里可以查任意骨骼名,
     * 因此旧版（无 {@code RightHandLocator}）模型也能取到 {@code RightArm} 的层级。</p>
     *
     * <p>返回空列表表示模型里没有这根骨骼。</p>
     */
    default List<? extends ILocationBone> boneHierarchy(String boneName) {
        return Collections.emptyList();
    }

    default List<List<? extends ILocationBone>> extraLeftHandBones() {
        return Collections.emptyList();
    }

    default List<? extends ILocationBone> rightHandBones() {
        return Collections.emptyList();
    }

    default List<List<? extends ILocationBone>> extraRightHandBones() {
        return Collections.emptyList();
    }

    default List<? extends ILocationBone> leftWaistBones() {
        return Collections.emptyList();
    }

    default List<? extends ILocationBone> rightWaistBones() {
        return Collections.emptyList();
    }

    default List<? extends ILocationBone> backpackBones() {
        return Collections.emptyList();
    }

    default List<? extends ILocationBone> tacPistolBones() {
        return Collections.emptyList();
    }

    default List<? extends ILocationBone> tacRifleBones() {
        return Collections.emptyList();
    }

    default List<? extends ILocationBone> headBones() {
        return Collections.emptyList();
    }
}
