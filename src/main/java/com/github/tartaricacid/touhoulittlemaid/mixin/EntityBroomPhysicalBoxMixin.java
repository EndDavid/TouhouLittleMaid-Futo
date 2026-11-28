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
