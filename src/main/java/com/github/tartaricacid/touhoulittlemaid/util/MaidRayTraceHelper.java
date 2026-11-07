package com.github.tartaricacid.touhoulittlemaid.util;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * 「玩家正看着哪只女仆」的判定（上游 {@code util/MaidRayTraceHelper} 的逐字移植）。
 *
 * <h2>为什么不用原版的实体拾取</h2>
 *
 * <p>{@code GameRenderer#pick} 的结果存在 {@code Minecraft#hitResult} 里，但它是
 * <b>客户端独有</b>的；相机的判定要跑在服务端（要真正删除实体并掉落照片），
 * 所以必须自己算一遍「玩家视线与实体的夹角」。</p>
 *
 * <h2>判据为什么写成「夹角余弦 &gt; 1 - 0.075/距离」</h2>
 *
 * <p>不能用固定角度阈值：远处一只 0.6 格宽的女仆在屏幕上只占几个像素，
 * 固定阈值会导致「准星明显偏了也还算命中」；反过来近距离时固定阈值又太严。</p>
 *
 * <p>上游的做法是让<b>允许的偏差随距离缩小</b>：
 * {@code maxDistanceDeviation = 0.075 / distance}，于是
 * {@code cosAngle > 1 - 0.075 / distance}。因为 {@code cos θ ≈ 1 - θ²/2}，
 * 这个式子等价于 {@code θ < sqrt(0.15 / distance)}（弧度），
 * 也就是「横向容差恒为 0.075 格」—— 距离越远角容差越小，但横向宽度不变。
 * 这与准星本身的语义一致。</p>
 *
 * <p>最后再加一道 {@code hasLineOfSight}，避免隔着墙把女仆拍走。</p>
 */
public final class MaidRayTraceHelper {
    private MaidRayTraceHelper() {
    }

    /**
     * 在玩家视线方向上找一只自己的女仆。
     *
     * @param player   玩家
     * @param distance 搜索盒半径（上游相机用 8）
     * @return 命中的女仆；没有则 {@link Optional#empty()}
     */
    public static Optional<EntityMaid> rayTraceMaid(Player player, double distance) {
        // 先按包围盒粗筛：只考虑自己的、活着的女仆
        List<Entity> maidList = player.level.getEntities(player, player.getBoundingBox()
                        .inflate(distance, distance, distance),
                entity -> entity instanceof EntityMaid maid && maid.isOwnedByPlayer(player));
        for (Entity maid : maidList) {
            // 二次判定，以防万一（上游同款注释）
            if (maid.isAlive() && maid instanceof EntityMaid entityMaid
                    && entityMaid.isOwnedByPlayer(player) && isEntityBeingLookedAt(player, maid)) {
                return Optional.of(entityMaid);
            }
        }
        return Optional.empty();
    }

    /**
     * 玩家的视线是否「足够对准」这个实体。
     *
     * <p>瞄准点取实体高度的 <b>2/3</b> 处而不是眼睛：女仆只有 1.5 格高，
     * 第三人称下准星通常落在她的躯干上，用眼睛高度会偏低。</p>
     */
    private static boolean isEntityBeingLookedAt(Player player, Entity lookEntity) {
        // 玩家朝向（单位向量）
        Vec3 lookVec = player.getLookAngle().normalize();
        // 玩家眼睛 → 实体瞄准点 的向量
        Vec3 playerAndEntityVec = new Vec3(lookEntity.getX() - player.getX(),
                lookEntity.getBoundingBox().minY + (double) lookEntity.getEyeHeight() * 2 / 3
                        - (player.getY() + (double) player.getEyeHeight()),
                lookEntity.getZ() - player.getZ());
        double playerAndEntityDistance = playerAndEntityVec.length();
        if (playerAndEntityDistance <= 1.0E-4D) {
            // 距离几乎为 0 时 normalize 会得到 NaN，直接当作「看得见」
            return player.hasLineOfSight(lookEntity);
        }
        playerAndEntityVec = playerAndEntityVec.normalize();
        // 两个单位向量点乘即为夹角余弦
        double cosAngle = lookVec.dot(playerAndEntityVec);
        // 允许的偏差与距离成反比 —— 等价于「横向容差恒为 0.075 格」（见类注释）
        double maxDistanceDeviation = 0.075D / playerAndEntityDistance;
        return cosAngle > 1.0D - maxDistanceDeviation && player.hasLineOfSight(lookEntity);
    }
}

