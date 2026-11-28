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
 * 让本模组的进度标签页<b>一开始就存在</b>。
 *
 * <p>1.18.2 的进度同步是渐进的：只有 {@code visible} 集合里的节点才会发给客户端，
 * 而该集合的入口只从「已完成的进度」出发 —— 一条都没点亮时连 root 都不会同步，
 * 客户端因此没有标签页。上游 1.20.1 的 {@code AdvancementVisibilityEvaluator}
 * 则把无 parent 的节点判为 {@code ALWAYS_SHOW} 并向下展开 2 层。</p>
 *
 * <p>这里精确复刻 1.20.1：{@code shouldBeVisible} 沿 parent 链向上找本模组的 root，
 * {@code load} 末尾从每个 root 出发写入 2 层节点。<b>必须限定命名空间</b>，
 * 否则原版 {@code minecraft:recipes/root} 会凭空多出一个「配方」标签。</p>
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

