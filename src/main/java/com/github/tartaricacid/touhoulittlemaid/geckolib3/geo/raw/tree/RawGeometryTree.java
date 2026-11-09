package com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.tree;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.Bone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.Cube;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.MinecraftGeometry;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.ModelProperties;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.RawGeoModel;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把「扁平的骨骼数组 + parent 名字」整理成骨骼树。
 *
 * <h2>为什么不能按名字直接落表</h2>
 *
 * <p>基岩格式允许<b>同名骨骼</b>：默认模型包里的 {@code kisume.json} 就声明了两根
 * {@code body}（一根只用作父节点、没有 cube，另一根承载身体 cube）。如果直接
 * {@code map.put(name, group)}，后一根会覆盖前一根，前一根的整棵子树就从树上脱落，
 * 它下面的骨骼永远找不到父级。</p>
 *
 * <p>而旧实现用的是「反复扫描、能挂就挂」的不动点循环：一旦有骨骼永远挂不上，
 * 列表就永远清不空，循环无限空转 —— 客户端表现为资源重载阶段彻底卡死。
 * 实测 {@code kisume.json} 会稳定触发（{@code bucket} 归属的那棵子树脱落，
 * 剩下 11 根 {@code parent=bucket} 的骨骼永远无法解析）。</p>
 *
 * <h2>现在的做法</h2>
 *
 * <ol>
 *     <li><b>一遍建节点</b>：同名且 pivot / rotation 完全一致的骨骼合并 cube
 *         （即「空壳父节点 + 实体骨骼」的写法），其余同名骨骼加 {@code #2} 后缀，
 *         保证一个名字只对应一根骨骼；</li>
 *     <li><b>一遍挂父级</b>：父级名字不存在、或指向自己时按顶层处理
 *         （基岩渲染器对坏数据也是这种宽容行为）；</li>
 *     <li><b>一遍断环</b>：从顶层做可达性标记，把藏在环里的骨骼断出来提升为顶层，
 *         保证「每根骨骼恰好出现一次」，递归遍历必然终止。</li>
 * </ol>
 *
 * <p>整体 O(n)，不再有循环重扫。</p>
 */
public class RawGeometryTree {
    public Map<String, RawBoneGroup> topLevelBones = new Object2ObjectOpenHashMap<>();
    public ModelProperties properties;

    public static RawGeometryTree parseHierarchy(RawGeoModel model) {
        RawGeometryTree hierarchy = new RawGeometryTree();
        MinecraftGeometry[] geometries = model.getMinecraftGeometry();
        if (geometries == null || geometries.length == 0) {
            return hierarchy;
        }
        MinecraftGeometry geometry = geometries[0];
        hierarchy.properties = geometry.getProperties();
        Bone[] rawBones = geometry.getBones();
        if (rawBones == null || rawBones.length == 0) {
            return hierarchy;
        }

        // 1) 建节点：同名骨骼合并或改名
        Map<String, RawBoneGroup> byName = new HashMap<>();
        List<RawBoneGroup> groups = new ArrayList<>(rawBones.length);
        for (Bone bone : rawBones) {
            String name = bone.getName() == null ? "" : bone.getName();
            RawBoneGroup existing = byName.get(name);
            if (existing != null && sameTransform(existing.selfBone, bone)) {
                // 「空壳父节点 + 实体骨骼」：把 cube 并进先出现的那根，
                // 挂在它下面的子骨骼与动画都不受影响。
                existing.selfBone.setCubes(concat(existing.selfBone.getCubes(), bone.getCubes()));
                if (existing.selfBone.getInflate() == null) {
                    existing.selfBone.setInflate(bone.getInflate());
                }
                if (existing.selfBone.getMirror() == null) {
                    existing.selfBone.setMirror(bone.getMirror());
                }
                continue;
            }
            if (existing != null) {
                int suffix = 2;
                while (byName.containsKey(name + "#" + suffix)) {
                    suffix++;
                }
                name = name + "#" + suffix;
                bone.setName(name);
            }
            RawBoneGroup group = new RawBoneGroup(bone);
            byName.put(name, group);
            groups.add(group);
        }

        // 2) 挂父级：父级缺失 / 自指都按顶层处理
        Map<RawBoneGroup, RawBoneGroup> parentOf = new IdentityHashMap<>();
        for (RawBoneGroup group : groups) {
            String parentName = group.selfBone.getParent();
            RawBoneGroup parent = parentName == null ? null : byName.get(parentName);
            if (parent == null || parent == group) {
                hierarchy.topLevelBones.put(group.selfBone.getName(), group);
            } else {
                parent.children.put(group.selfBone.getName(), group);
                parentOf.put(group, parent);
            }
        }

        // 3) 断环：把不可达（即藏在环里）的骨骼提升为顶层
        Set<RawBoneGroup> reachable = Collections.newSetFromMap(new IdentityHashMap<>());
        for (RawBoneGroup top : hierarchy.topLevelBones.values()) {
            markReachable(top, reachable);
        }
        for (RawBoneGroup group : groups) {
            if (reachable.contains(group)) {
                continue;
            }
            RawBoneGroup parent = parentOf.get(group);
            if (parent != null) {
                parent.children.remove(group.selfBone.getName());
            }
            hierarchy.topLevelBones.put(group.selfBone.getName(), group);
            markReachable(group, reachable);
        }
        return hierarchy;
    }

    /** 深度优先标记可达骨骼；{@code visited} 同时充当环的护栏。 */
    private static void markReachable(RawBoneGroup group, Set<RawBoneGroup> visited) {
        if (!visited.add(group)) {
            return;
        }
        for (RawBoneGroup child : group.children.values()) {
            markReachable(child, visited);
        }
    }

    private static boolean sameTransform(Bone left, Bone right) {
        return java.util.Arrays.equals(left.getPivot(), right.getPivot())
                && java.util.Arrays.equals(left.getRotation(), right.getRotation());
    }

    private static Cube[] concat(Cube[] first, Cube[] second) {
        if (second == null || second.length == 0) {
            return first;
        }
        if (first == null || first.length == 0) {
            return second;
        }
        Cube[] merged = new Cube[first.length + second.length];
        System.arraycopy(first, 0, merged, 0, first.length);
        System.arraycopy(second, 0, merged, first.length, second.length);
        return merged;
    }
}

