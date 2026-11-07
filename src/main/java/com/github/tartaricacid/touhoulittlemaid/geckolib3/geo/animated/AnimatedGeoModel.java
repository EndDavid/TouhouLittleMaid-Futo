package com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.processor.ILocationBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;

public class AnimatedGeoModel implements ILocationModel {
    private final GeoModel geoModel;
    private final List<AnimatedGeoBone> topLevelBones;
    private final Map<String, AnimatedGeoBone> bones;

    private final List<AnimatedGeoBone> leftHandBones;
    private final List<AnimatedGeoBone> rightHandBones;
    private final List<AnimatedGeoBone> leftWaistBones;
    private final List<AnimatedGeoBone> rightWaistBones;
    private final List<AnimatedGeoBone> backpackBones;
    private final List<AnimatedGeoBone> tacPistolBones;
    private final List<AnimatedGeoBone> tacRifleBones;
    private final List<AnimatedGeoBone> headBones;

    @Nullable
    private final AnimatedGeoBone head;
    @Nullable
    private final AnimatedGeoBone hat;
    @Nullable
    private final AnimatedGeoBone leftArm;
    @Nullable
    private final AnimatedGeoBone rightArm;

    public AnimatedGeoModel(GeoModel model) {
        geoModel = model;

        var bones = new Object2ObjectOpenHashMap<String, AnimatedGeoBone>();
        this.topLevelBones = ObjectLists.unmodifiable(new ObjectArrayList<>(model.topLevelBones().stream().map(b -> new AnimatedGeoBone(b, bones)).toList()));
        this.bones = Object2ObjectMaps.unmodifiable(bones);

        this.leftHandBones = getLocatorHierarchy("LeftHandLocator");
        this.rightHandBones = getLocatorHierarchy("RightHandLocator");
        this.leftWaistBones = getLocatorHierarchy("LeftWaistLocator");
        this.rightWaistBones = getLocatorHierarchy("RightWaistLocator");
        this.backpackBones = getLocatorHierarchy("BackpackLocator");
        this.tacPistolBones = getLocatorHierarchy("PistolLocator");
        this.tacRifleBones = getLocatorHierarchy("RifleLocator");
        this.headBones = getLocatorHierarchy("Head");

        this.head = bones.get("Head");
        // fixme: 有 hat 部分吗？
        this.hat = bones.get("Hat");
        this.leftArm = bones.get("LeftArm");
        this.rightArm = bones.get("RightArm");
    }

    private List<AnimatedGeoBone> getLocatorHierarchy(String locatorName) {
        var bone = this.bones.get(locatorName);
        if (bone == null) {
            return ObjectLists.emptyList();
        }

        var list = new ObjectArrayList<AnimatedGeoBone>();
        var visited = new HashSet<String>();
        while (true) {
            // 环检测：模型数据里出现 A.parent=B、B.parent=A 时（旧版模型改写后可能产生），
            // 无环检测的遍历会在这里无限循环，表现为资源重载/渲染卡死。
            if (!visited.add(bone.geoBone().name())) {
                break;
            }
            list.add(bone);
            if (bone.geoBone().parent() == null) {
                break;
            }
            AnimatedGeoBone parent = this.bones.get(bone.geoBone().parent().name());
            if (parent == null) {
                break;
            }
            bone = parent;
        }
        Collections.reverse(list);
        return ObjectLists.unmodifiable(list);
    }

    public GeoModel geoModel() {
        return geoModel;
    }

    public List<AnimatedGeoBone> topLevelBones() {
        return topLevelBones;
    }

    public Map<String, AnimatedGeoBone> bones() {
        return bones;
    }

    public List<AnimatedGeoBone> leftHandBones() {
        return leftHandBones;
    }

    public List<AnimatedGeoBone> rightHandBones() {
        return rightHandBones;
    }

    public List<AnimatedGeoBone> leftWaistBones() {
        return leftWaistBones;
    }

    public List<AnimatedGeoBone> rightWaistBones() {
        return rightWaistBones;
    }

    public List<AnimatedGeoBone> backpackBones() {
        return backpackBones;
    }

    public List<AnimatedGeoBone> tacPistolBones() {
        return tacPistolBones;
    }

    public List<AnimatedGeoBone> tacRifleBones() {
        return tacRifleBones;
    }

    public List<AnimatedGeoBone> headBones() {
        return headBones;
    }

    @Nullable
    public AnimatedGeoBone head() {
        return head;
    }

    @Nullable
    public AnimatedGeoBone hat() {
        return hat;
    }

    @Nullable
    public AnimatedGeoBone leftArm() {
        return leftArm;
    }

    @Nullable
    public AnimatedGeoBone rightArm() {
        return rightArm;
    }

    /**
     * 按骨骼名取父级链，供旧版（无 {@code *HandLocator}）模型的手持物品定位使用。
     */
    @Override
    public List<? extends ILocationBone> boneHierarchy(String boneName) {
        return getLocatorHierarchy(boneName);
    }
}
