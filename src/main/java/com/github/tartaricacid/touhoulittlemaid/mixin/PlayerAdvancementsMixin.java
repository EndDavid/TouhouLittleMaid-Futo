package com.github.tartaricacid.touhoulittlemaid.mixin;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Set;

/**
 * 让本模组的 4 个进度标签页<b>一开始就存在</b>。
 *
 * <h2>现象与根因</h2>
 * <p>1.18.2 的进度同步是<b>渐进</b>的：{@link PlayerAdvancements} 维护一个 {@code visible}
 * 集合，只有它的元素才会被 {@code ClientboundUpdateAdvancementsPacket} 发出去；
 * 而客户端的标签页又只由「收到的、{@code parent == null} 的进度」生成
 * （{@code AdvancementList#add} -> {@code onAddAdvancementRoot} -> {@code AdvancementTab#create}）。
 * 所以<b>没被同步的 root，就等于不存在的标签页</b>。</p>
 *
 * <p>1.18.2 的 {@code shouldBeVisible} 要求「自己或子树有点亮过」才把节点算作可见，
 * 且可见性传播的入口 {@code ensureAllVisible} <b>只从「已完成的进度」出发</b>。
 * 于是新存档里一条都没点亮时，整棵树（连 root 一起）都不会发给客户端。</p>
 *
 * <p>实测印证：用 2026-11-24 那次的存档进度跑同一套算法，可见集正好 214 条，
 * 与客户端日志的 {@code Loaded 214 advancements} 逐字吻合；其中可见的 root 只有
 * {@code story / adventure / husbandry} 三个，正是玩家看到的三个原版标签。</p>
 *
 * <h2>与上游的差异</h2>
 * <p>上游 1.20.1 用的是 {@code AdvancementVisibilityEvaluator}：没有 parent 的节点判为
 * {@code ALWAYS_SHOW}，再配合 2 层栈深，等价于「root 及其往下两代可见」，
 * 且是从 root 出发整棵树遍历。1.18.2 既没有 {@code ALWAYS_SHOW}，入口也不是 root。
 * 本数据文件与上游逐字相同，所以这不是搬错数据，而是版本行为差异。</p>
 *
 * <h2>本 mixin 做的事（精确复刻 1.20.1，仅限本模组命名空间）</h2>
 * <ol>
 *   <li>{@code shouldBeVisible}：沿 parent 链向上最多 2 层，若途中摸到
 *       <b>本模组的</b> root 则判为可见 —— 等价于 1.20.1 的 {@code ALWAYS_SHOW}；</li>
 *   <li>{@code load} 末尾（登录与 {@code /reload} 都会走到）：从本模组的每个 root
 *       出发按同样的 2 层深度把节点写入 {@code visible} / {@code visibilityChanged}，
 *       让首次同步包把它们带上。</li>
 * </ol>
 *
 * <p>限定命名空间很重要：原版 {@code minecraft:recipes/root} 有 display 却永远完不成，
 * 若对所有命名空间都套用 {@code ALWAYS_SHOW}，进度页会凭空多出一个「配方」标签。</p>
 *
 * <p>数据里没有任何 {@code "hidden": true}，且无 display 的两条
 * （{@code give_smart_slab}、{@code grant_book_on_first_join}）会被跳过 ——
 * 它们本来就不产生标签页，与上游一致。</p>
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    /** 与 1.20.1 {@code AdvancementVisibilityEvaluator} 的 {@code VISIBILITY_DEPTH} 相同。 */
    private static final int TLM_VISIBILITY_DEPTH = 2;

    @Shadow
    @Final
    private Set<Advancement> visible;

    @Shadow
    @Final
    private Set<Advancement> visibilityChanged;

    @Shadow
    @Final
    private Set<Advancement> progressChanged;

    @Shadow
    @Final
    private Map<Advancement, AdvancementProgress> advancements;

    /**
     * 1.20.1 的规则里「没有 parent 的节点」是 {@code ALWAYS_SHOW}，
     * 于是 root 本身一定可见、且 root 往下两代也会因为栈里摸到 {@code ALWAYS_SHOW} 而可见。
     * 1.18.2 没有这个概念，root 同样要「自己或子树有点亮」才可见。
     *
     * <p>这里只对<b>本模组命名空间</b>补齐 {@code ALWAYS_SHOW}：不能对所有命名空间生效，
     * 否则原版 {@code minecraft:recipes/root}（带 display、永远完不成）会凭空多出「配方」标签页。</p>
     */
    @Inject(method = "shouldBeVisible", at = @At("HEAD"), cancellable = true)
    private void tlm$treatOwnRootAsAlwaysShow(Advancement advancement,
                                              CallbackInfoReturnable<Boolean> cir) {
        Advancement cursor = advancement;
        for (int depth = 0; depth <= TLM_VISIBILITY_DEPTH && cursor != null; depth++) {
            // 链上出现没有 display 的节点：1.20.1 判 HIDDEN，交回原逻辑
            if (cursor.getDisplay() == null) {
                return;
            }
            if (cursor.getParent() == null) {
                if (TouhouLittleMaid.MOD_ID.equals(cursor.getId().getNamespace())) {
                    cir.setReturnValue(true);
                }
                return;
            }
            cursor = cursor.getParent();
        }
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void tlm$publishOwnRootsOnLoad(ServerAdvancementManager manager, CallbackInfo ci) {
        if (manager == null) {
            return;
        }
        for (Advancement advancement : manager.getAllAdvancements()) {
            if (!TouhouLittleMaid.MOD_ID.equals(advancement.getId().getNamespace())) {
                continue;
            }
            if (advancement.getParent() != null) {
                continue;
            }
            this.tlm$publishSubtree(advancement, 0);
        }
    }

    /**
     * 按 1.20.1 的 2 层深度把子树写进待同步集合。
     *
     * <p>不直接调用原版 {@code ensureVisibility}：那是 private，且不限制深度会往深处扩散。</p>
     */
    private void tlm$publishSubtree(Advancement advancement, int depth) {
        if (advancement == null || depth > TLM_VISIBILITY_DEPTH) {
            return;
        }
        // 与 1.20.1 的 HIDDEN 规则一致：没有 display 的节点及其子树都不发
        if (advancement.getDisplay() == null) {
            return;
        }
        this.visible.add(advancement);
        this.visibilityChanged.add(advancement);
        // 已经点亮的（例如开卷奖励）要把进度一起发，否则客户端会画成未完成
        if (this.advancements.containsKey(advancement)) {
            this.progressChanged.add(advancement);
        }
        for (Advancement child : advancement.getChildren()) {
            this.tlm$publishSubtree(child, depth + 1);
        }
    }
}

