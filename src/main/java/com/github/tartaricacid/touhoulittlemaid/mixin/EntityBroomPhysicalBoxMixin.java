package com.github.tartaricacid.touhoulittlemaid.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让扫帚在<b>解算方块碰撞的那一句</b>里使用「长高一格」的物理碰撞箱 ——
 * 行为对齐上游 1.20.1 的 {@code mixin/EntityMixin}。
 *
 * <h2>要解决的问题</h2>
 * <p>人 + 女仆同乘时，真实高度远超扫帚自己的 0.5625 格。碰撞箱不变的话，
 * 飞过单格空隙时上半身会直接穿过去；但如果把真实碰撞箱改大（本移植版最初就是这么写的，
 * 直接覆写 {@code EntityBroom#makeBoundingBox()}），问题更糟：</p>
 * <ul>
 *     <li>真实 {@code bb} 会随<b>乘客数</b>变化，而乘客数是同步状态；</li>
 *     <li>女仆上/下扫帚的那几 tick，服务端与客户端的高度差一整格；</li>
 *     <li>同一个位置在一端撞住、另一端是空的 → 位置包来回纠正 →
 *         玩家靠近扫帚时被反复推开、抖动（就是「卡碰撞箱」那个手感）。</li>
 * </ul>
 *
 * <h2>做法</h2>
 * <p>真实碰撞箱永远保持小箱子（{@code makeBoundingBox()} 不再覆写），
 * 只在 {@code Entity#collide(Vec3)} 取箱子那一次换成
 * {@link EntityBroom#getPhysicalBoundingBox()}。</p>
 *
 * <h2>与上游的区别（结构不同，行为相同）</h2>
 * <p>上游 1.20.1 是「{@code move()} 调 {@code collide()} 前后开关一个 {@code inPhysicalCheck} 标志，
 * 再在 {@code getBoundingBox()} 的 RETURN 上按标志换箱子」。这里改成只重定向
 * {@code collide} 内部那一个调用点，原因有三：</p>
 * <ol>
 *     <li>{@code getBoundingBox()} 是热路径 —— 每个实体的每次移动、碰撞、渲染、寻路都会调它，
 *         在 RETURN 上做全局判断，等于给全服每个实体每 tick 白加一次 {@code instanceof}；</li>
 *     <li>单点重定向没有跨方法的状态，也就没有「其它模组中途抛异常导致标志位永久为 true」的风险
 *         （上游的注释里也提到了这个担忧，只是它用 shift = AFTER 的第二个注入点绕开）；</li>
 *     <li>核对过 1.18.2 的字节码：{@code collide} 里 {@code getBoundingBox()} 只出现一次（偏移 1），
 *         而 {@code Entity#move} 里另一个 {@code getBoundingBox()} 调用在偏移 990，
 *         已经远在 {@code collide}（偏移 132）返回之后 —— 上游那两个注入点的作用范围也就是
 *         {@code collide} 这一次调用，所以两种写法的覆盖范围完全等价。</li>
 * </ol>
 */
@Mixin(Entity.class)
public class EntityBroomPhysicalBoxMixin {
    @Redirect(
            method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;")
    )
    private AABB futo$physicalBoundingBoxForCollide(Entity self) {
        // self 就是被重定向的那次调用的接收者，也就是 collide 里的 this
        if (self instanceof EntityBroom broom) {
            return broom.getPhysicalBoundingBox();
        }
        return self.getBoundingBox();
    }
}
